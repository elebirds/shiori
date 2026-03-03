package moe.hhm.shiori.order.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.client.ProductDetailSnapshot;
import moe.hhm.shiori.order.client.ProductServiceClient;
import moe.hhm.shiori.order.client.ProductSkuSnapshot;
import moe.hhm.shiori.order.config.OrderMqProperties;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.domain.OrderStatus;
import moe.hhm.shiori.order.domain.OutboxStatus;
import moe.hhm.shiori.order.dto.CreateOrderItem;
import moe.hhm.shiori.order.dto.CreateOrderRequest;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.event.EventEnvelope;
import moe.hhm.shiori.order.event.OrderCanceledPayload;
import moe.hhm.shiori.order.event.OrderCreatedPayload;
import moe.hhm.shiori.order.event.OrderPaidPayload;
import moe.hhm.shiori.order.event.OrderTimeoutPayload;
import moe.hhm.shiori.order.model.OrderEntity;
import moe.hhm.shiori.order.model.OrderItemEntity;
import moe.hhm.shiori.order.model.OrderItemRecord;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.model.OutboxEventEntity;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderCommandService {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);
    private static final String EVENT_ORDER_CREATED = "OrderCreated";
    private static final String EVENT_ORDER_PAID = "OrderPaid";
    private static final String EVENT_ORDER_TIMEOUT = "OrderTimeout";
    private static final String EVENT_ORDER_CANCELED = "OrderCanceled";

    private final OrderMapper orderMapper;
    private final ProductServiceClient productServiceClient;
    private final OrderProperties orderProperties;
    private final OrderMqProperties orderMqProperties;
    private final ObjectMapper objectMapper;

    public OrderCommandService(OrderMapper orderMapper,
                               ProductServiceClient productServiceClient,
                               OrderProperties orderProperties,
                               OrderMqProperties orderMqProperties,
                               ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.productServiceClient = productServiceClient;
        this.orderProperties = orderProperties;
        this.orderMqProperties = orderMqProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateOrderResponse createOrder(Long buyerUserId,
                                           List<String> roles,
                                           String idempotencyKey,
                                           CreateOrderRequest request) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        String existedOrderNo = orderMapper.findOrderNoByBuyerAndIdempotencyKey(buyerUserId, idempotencyKey);
        if (StringUtils.hasText(existedOrderNo)) {
            return buildIdempotentCreateResponse(existedOrderNo);
        }

        List<PreparedOrderLine> lines = prepareOrderLines(buyerUserId, roles, request);
        long totalAmountCent = lines.stream().mapToLong(PreparedOrderLine::subtotalCent).sum();
        int itemCount = lines.stream().mapToInt(PreparedOrderLine::quantity).sum();
        String orderNo = generateOrderNo();
        LocalDateTime timeoutAt = LocalDateTime.now().plusMinutes(orderProperties.getTimeoutMinutes());

        List<PreparedOrderLine> deducted = new ArrayList<>();
        try {
            for (PreparedOrderLine line : lines) {
                productServiceClient.deductStock(
                        line.skuId(),
                        line.quantity(),
                        stockBizNo(orderNo, line.skuId()),
                        buyerUserId,
                        roles);
                deducted.add(line);
            }
        } catch (RuntimeException ex) {
            compensateReleased(orderNo, buyerUserId, roles, deducted);
            throw ex;
        }

        try {
            orderMapper.insertCreateIdempotency(buyerUserId, idempotencyKey, orderNo);
        } catch (DuplicateKeyException ex) {
            compensateReleased(orderNo, buyerUserId, roles, deducted);
            return buildIdempotentCreateResponseFromKey(buyerUserId, idempotencyKey);
        }

        try {
            persistOrder(orderNo, buyerUserId, lines.getFirst().sellerUserId(), totalAmountCent, itemCount, timeoutAt, lines);
            appendOrderCreatedOutbox(orderNo, buyerUserId, lines.getFirst().sellerUserId(), totalAmountCent, itemCount);
            appendOrderTimeoutOutbox(orderNo, buyerUserId);
            return new CreateOrderResponse(orderNo, OrderStatus.UNPAID.name(), totalAmountCent, itemCount, false);
        } catch (RuntimeException ex) {
            compensateReleased(orderNo, buyerUserId, roles, deducted);
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse payOrder(Long buyerUserId, String orderNo, String paymentNo) {
        if (!StringUtils.hasText(paymentNo)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        OrderRecord order = requireOrder(orderNo);
        ensureBuyer(order, buyerUserId);

        OrderStatus status = OrderStatus.fromCode(order.status());
        if (status == OrderStatus.PAID) {
            if (Objects.equals(order.paymentNo(), paymentNo)) {
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_PAYMENT_CONFLICT, HttpStatus.CONFLICT);
        }
        if (status != OrderStatus.UNPAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        int affected;
        try {
            affected = orderMapper.markOrderPaid(
                    orderNo,
                    buyerUserId,
                    paymentNo,
                    LocalDateTime.now(),
                    OrderStatus.UNPAID.getCode(),
                    OrderStatus.PAID.getCode()
            );
        } catch (DuplicateKeyException ex) {
            OrderRecord paymentOrder = orderMapper.findOrderByPaymentNo(paymentNo);
            if (paymentOrder != null && orderNo.equals(paymentOrder.orderNo())) {
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_PAYMENT_CONFLICT, HttpStatus.CONFLICT);
        }

        if (affected == 0) {
            OrderRecord latest = requireOrder(orderNo);
            if (OrderStatus.fromCode(latest.status()) == OrderStatus.PAID
                    && Objects.equals(latest.paymentNo(), paymentNo)) {
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        appendOrderPaidOutbox(orderNo, paymentNo, order.buyerUserId(), order.sellerUserId(), order.totalAmountCent());
        return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse cancelOrder(Long buyerUserId, List<String> roles, String orderNo, String reason) {
        OrderRecord order = requireOrder(orderNo);
        ensureBuyer(order, buyerUserId);

        OrderStatus status = OrderStatus.fromCode(order.status());
        if (status == OrderStatus.CANCELED) {
            return new OrderOperateResponse(orderNo, OrderStatus.CANCELED.name(), true);
        }
        if (status != OrderStatus.UNPAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        int affected = orderMapper.cancelOrderAsBuyer(
                orderNo,
                buyerUserId,
                resolveCancelReason(reason),
                OrderStatus.UNPAID.getCode(),
                OrderStatus.CANCELED.getCode()
        );
        if (affected == 0) {
            OrderRecord latest = requireOrder(orderNo);
            if (OrderStatus.fromCode(latest.status()) == OrderStatus.CANCELED) {
                return new OrderOperateResponse(orderNo, OrderStatus.CANCELED.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        List<OrderItemRecord> items = orderMapper.listOrderItemsByOrderNo(orderNo);
        releaseOrderItems(orderNo, buyerUserId, roles, items);
        appendOrderCanceledOutbox(orderNo, order.buyerUserId(), order.sellerUserId(), resolveCancelReason(reason));
        return new OrderOperateResponse(orderNo, OrderStatus.CANCELED.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse cancelOrderAsAdmin(Long operatorUserId,
                                                   List<String> roles,
                                                   String orderNo,
                                                   String reason) {
        OrderRecord before = requireOrder(orderNo);
        OrderStatus status = OrderStatus.fromCode(before.status());
        String normalizedReason = resolveCancelReason(reason);

        if (status == OrderStatus.CANCELED) {
            insertAdminAudit(operatorUserId, orderNo, "ORDER_ADMIN_CANCEL", before, before, normalizedReason);
            return new OrderOperateResponse(orderNo, OrderStatus.CANCELED.name(), true);
        }
        if (status != OrderStatus.UNPAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        int affected = orderMapper.cancelOrderByTimeout(
                orderNo,
                normalizedReason,
                OrderStatus.UNPAID.getCode(),
                OrderStatus.CANCELED.getCode()
        );
        if (affected == 0) {
            OrderRecord latest = requireOrder(orderNo);
            if (OrderStatus.fromCode(latest.status()) == OrderStatus.CANCELED) {
                insertAdminAudit(operatorUserId, orderNo, "ORDER_ADMIN_CANCEL", before, latest, normalizedReason);
                return new OrderOperateResponse(orderNo, OrderStatus.CANCELED.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        List<OrderItemRecord> items = orderMapper.listOrderItemsByOrderNo(orderNo);
        releaseOrderItems(orderNo, operatorUserId, roles, items);
        appendOrderCanceledOutbox(orderNo, before.buyerUserId(), before.sellerUserId(), normalizedReason);
        OrderRecord after = requireOrder(orderNo);
        insertAdminAudit(operatorUserId, orderNo, "ORDER_ADMIN_CANCEL", before, after, normalizedReason);
        return new OrderOperateResponse(orderNo, OrderStatus.CANCELED.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleTimeout(String orderNo) {
        OrderRecord order = orderMapper.findOrderByOrderNo(orderNo);
        if (order == null || isDeleted(order)) {
            return;
        }
        if (OrderStatus.fromCode(order.status()) != OrderStatus.UNPAID) {
            return;
        }

        int affected = orderMapper.cancelOrderByTimeout(
                orderNo,
                "超时未支付自动取消",
                OrderStatus.UNPAID.getCode(),
                OrderStatus.CANCELED.getCode()
        );
        if (affected == 0) {
            return;
        }

        List<OrderItemRecord> items = orderMapper.listOrderItemsByOrderNo(orderNo);
        releaseOrderItems(orderNo, order.buyerUserId(), List.of("ROLE_USER"), items);
        appendOrderCanceledOutbox(orderNo, order.buyerUserId(), order.sellerUserId(), "超时未支付自动取消");
    }

    private void persistOrder(String orderNo,
                              Long buyerUserId,
                              Long sellerUserId,
                              long totalAmountCent,
                              int itemCount,
                              LocalDateTime timeoutAt,
                              List<PreparedOrderLine> lines) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderNo(orderNo);
        orderEntity.setBuyerUserId(buyerUserId);
        orderEntity.setSellerUserId(sellerUserId);
        orderEntity.setStatus(OrderStatus.UNPAID.getCode());
        orderEntity.setTotalAmountCent(totalAmountCent);
        orderEntity.setItemCount(itemCount);
        orderEntity.setTimeoutAt(timeoutAt);
        orderMapper.insertOrder(orderEntity);

        if (orderEntity.getId() == null) {
            throw new IllegalStateException("创建订单后未返回主键");
        }
        List<OrderItemEntity> entities = new ArrayList<>(lines.size());
        for (PreparedOrderLine line : lines) {
            OrderItemEntity entity = new OrderItemEntity();
            entity.setOrderId(orderEntity.getId());
            entity.setOrderNo(orderNo);
            entity.setProductId(line.productId());
            entity.setProductNo(line.productNo());
            entity.setSkuId(line.skuId());
            entity.setSkuNo(line.skuNo());
            entity.setSkuName(line.skuName());
            entity.setSpecJson(line.specJson());
            entity.setPriceCent(line.priceCent());
            entity.setQuantity(line.quantity());
            entity.setSubtotalCent(line.subtotalCent());
            entity.setOwnerUserId(line.sellerUserId());
            entities.add(entity);
        }
        orderMapper.batchInsertOrderItems(entities);
    }

    private List<PreparedOrderLine> prepareOrderLines(Long buyerUserId, List<String> roles, CreateOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new BizException(OrderErrorCode.ORDER_EMPTY_ITEMS, HttpStatus.BAD_REQUEST);
        }

        Map<String, AggregatedOrderItem> aggregated = new LinkedHashMap<>();
        for (CreateOrderItem item : request.items()) {
            if (item == null || item.productId() == null || item.skuId() == null || item.quantity() == null
                    || item.quantity() <= 0) {
                throw new BizException(OrderErrorCode.ORDER_ITEM_INVALID, HttpStatus.BAD_REQUEST);
            }
            String key = item.productId() + ":" + item.skuId();
            AggregatedOrderItem existed = aggregated.get(key);
            if (existed == null) {
                aggregated.put(key, new AggregatedOrderItem(item.productId(), item.skuId(), item.quantity()));
            } else {
                aggregated.put(key, new AggregatedOrderItem(
                        existed.productId(),
                        existed.skuId(),
                        existed.quantity() + item.quantity()
                ));
            }
        }

        Map<Long, ProductDetailSnapshot> productCache = new HashMap<>();
        List<PreparedOrderLine> lines = new ArrayList<>(aggregated.size());
        Long sellerUserId = null;
        for (AggregatedOrderItem item : aggregated.values()) {
            ProductDetailSnapshot product = productCache.computeIfAbsent(item.productId(),
                    id -> productServiceClient.getProductDetail(id, buyerUserId, roles));
            if (product == null || !"ON_SALE".equals(product.status())) {
                throw new BizException(OrderErrorCode.ORDER_PRODUCT_INVALID, HttpStatus.BAD_REQUEST);
            }
            if (Objects.equals(buyerUserId, product.ownerUserId())) {
                throw new BizException(OrderErrorCode.ORDER_SELF_PURCHASE_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
            }
            if (sellerUserId == null) {
                sellerUserId = product.ownerUserId();
            } else if (!sellerUserId.equals(product.ownerUserId())) {
                throw new BizException(OrderErrorCode.ORDER_CROSS_SELLER_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
            }

            ProductSkuSnapshot sku = findSku(product, item.skuId());
            if (sku == null || sku.priceCent() == null || sku.priceCent() < 0) {
                throw new BizException(OrderErrorCode.ORDER_ITEM_INVALID, HttpStatus.BAD_REQUEST);
            }

            long subtotal;
            try {
                subtotal = Math.multiplyExact(sku.priceCent(), item.quantity());
            } catch (ArithmeticException ex) {
                throw new BizException(OrderErrorCode.ORDER_ITEM_INVALID, HttpStatus.BAD_REQUEST);
            }

            lines.add(new PreparedOrderLine(
                    item.productId(),
                    product.productNo(),
                    item.skuId(),
                    sku.skuNo(),
                    sku.skuName(),
                    sku.specJson(),
                    sku.priceCent(),
                    item.quantity(),
                    subtotal,
                    product.ownerUserId()
            ));
        }

        if (lines.isEmpty()) {
            throw new BizException(OrderErrorCode.ORDER_EMPTY_ITEMS, HttpStatus.BAD_REQUEST);
        }
        return lines;
    }

    private ProductSkuSnapshot findSku(ProductDetailSnapshot product, Long skuId) {
        if (product.skus() == null) {
            return null;
        }
        for (ProductSkuSnapshot sku : product.skus()) {
            if (Objects.equals(skuId, sku.skuId())) {
                return sku;
            }
        }
        return null;
    }

    private void releaseOrderItems(String orderNo, Long buyerUserId, List<String> roles, List<OrderItemRecord> items) {
        for (OrderItemRecord item : items) {
            productServiceClient.releaseStock(
                    item.skuId(),
                    item.quantity(),
                    stockBizNo(orderNo, item.skuId()),
                    buyerUserId,
                    roles
            );
        }
    }

    private void compensateReleased(String orderNo, Long buyerUserId, List<String> roles, List<PreparedOrderLine> deducted) {
        for (PreparedOrderLine line : deducted) {
            try {
                productServiceClient.releaseStock(
                        line.skuId(),
                        line.quantity(),
                        stockBizNo(orderNo, line.skuId()),
                        buyerUserId,
                        roles
                );
            } catch (RuntimeException ex) {
                log.error("创建订单失败后回补库存失败, orderNo={}, skuId={}, err={}",
                        orderNo, line.skuId(), ex.getMessage());
            }
        }
    }

    private void appendOrderCreatedOutbox(String orderNo, Long buyerUserId, Long sellerUserId, long totalAmountCent, int itemCount) {
        OrderCreatedPayload payload = new OrderCreatedPayload(orderNo, buyerUserId, sellerUserId, totalAmountCent, itemCount);
        appendOutbox(orderNo, EVENT_ORDER_CREATED, payload, orderMqProperties.getEventExchange(),
                orderMqProperties.getOrderCreatedRoutingKey());
    }

    private void appendOrderTimeoutOutbox(String orderNo, Long buyerUserId) {
        OrderTimeoutPayload payload = new OrderTimeoutPayload(orderNo, buyerUserId);
        appendOutbox(orderNo, EVENT_ORDER_TIMEOUT, payload, orderMqProperties.getDelayExchange(),
                orderMqProperties.getDelayRoutingKey());
    }

    private void appendOrderPaidOutbox(String orderNo, String paymentNo, Long buyerUserId, Long sellerUserId, Long totalAmountCent) {
        OrderPaidPayload buyerPayload = new OrderPaidPayload(
                orderNo, paymentNo, buyerUserId, "BUYER", buyerUserId, sellerUserId, totalAmountCent);
        appendOutbox(orderNo, EVENT_ORDER_PAID, buyerPayload, orderMqProperties.getEventExchange(),
                orderMqProperties.getOrderPaidRoutingKey());

        OrderPaidPayload sellerPayload = new OrderPaidPayload(
                orderNo, paymentNo, sellerUserId, "SELLER", buyerUserId, sellerUserId, totalAmountCent);
        appendOutbox(orderNo, EVENT_ORDER_PAID, sellerPayload, orderMqProperties.getEventExchange(),
                orderMqProperties.getOrderPaidRoutingKey());
    }

    private void appendOrderCanceledOutbox(String orderNo, Long buyerUserId, Long sellerUserId, String reason) {
        OrderCanceledPayload payload = new OrderCanceledPayload(orderNo, buyerUserId, sellerUserId, reason);
        appendOutbox(orderNo, EVENT_ORDER_CANCELED, payload, orderMqProperties.getEventExchange(),
                orderMqProperties.getOrderCanceledRoutingKey());
    }

    private void appendOutbox(String aggregateId, String type, Object payload, String exchange, String routingKey) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                type,
                aggregateId,
                Instant.now().toString(),
                objectMapper.valueToTree(payload)
        );
        String envelopeJson;
        try {
            envelopeJson = objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("构建 outbox 事件失败", e);
        }

        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setEventId(envelope.eventId());
        entity.setAggregateId(aggregateId);
        entity.setType(type);
        entity.setPayload(envelopeJson);
        entity.setExchangeName(exchange);
        entity.setRoutingKey(routingKey);
        entity.setStatus(OutboxStatus.PENDING.name());
        entity.setRetryCount(0);
        orderMapper.insertOutboxEvent(entity);
    }

    private String resolveCancelReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : "用户主动取消";
    }

    private void insertAdminAudit(Long operatorUserId,
                                  String orderNo,
                                  String action,
                                  OrderRecord before,
                                  OrderRecord after,
                                  String reason) {
        orderMapper.insertAdminAuditLog(
                operatorUserId,
                orderNo,
                action,
                snapshot(before),
                snapshot(after),
                StringUtils.hasText(reason) ? reason.trim() : null
        );
    }

    private String snapshot(OrderRecord record) {
        Map<String, Object> state = Map.of(
                "status", OrderStatus.fromCode(record.status()).name(),
                "buyerUserId", record.buyerUserId(),
                "sellerUserId", record.sellerUserId(),
                "paymentNo", record.paymentNo() == null ? "" : record.paymentNo(),
                "totalAmountCent", record.totalAmountCent()
        );
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private void ensureBuyer(OrderRecord order, Long buyerUserId) {
        if (!order.buyerUserId().equals(buyerUserId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    private OrderRecord requireOrder(String orderNo) {
        OrderRecord record = orderMapper.findOrderByOrderNo(orderNo);
        if (record == null || isDeleted(record)) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return record;
    }

    private boolean isDeleted(OrderRecord record) {
        return record.isDeleted() != null && record.isDeleted() == 1;
    }

    private CreateOrderResponse buildIdempotentCreateResponseFromKey(Long buyerUserId, String idempotencyKey) {
        String existedOrderNo = orderMapper.findOrderNoByBuyerAndIdempotencyKey(buyerUserId, idempotencyKey);
        if (!StringUtils.hasText(existedOrderNo)) {
            throw new BizException(OrderErrorCode.ORDER_DUPLICATE_REQUEST, HttpStatus.CONFLICT);
        }
        return buildIdempotentCreateResponse(existedOrderNo);
    }

    private CreateOrderResponse buildIdempotentCreateResponse(String orderNo) {
        OrderRecord order = orderMapper.findOrderByOrderNo(orderNo);
        if (order == null || isDeleted(order)) {
            throw new BizException(OrderErrorCode.ORDER_DUPLICATE_REQUEST, HttpStatus.CONFLICT);
        }
        return new CreateOrderResponse(
                order.orderNo(),
                OrderStatus.fromCode(order.status()).name(),
                order.totalAmountCent(),
                order.itemCount(),
                true
        );
    }

    private String stockBizNo(String orderNo, Long skuId) {
        return orderNo + ":" + skuId;
    }

    private String generateOrderNo() {
        return "O" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private record AggregatedOrderItem(Long productId, Long skuId, Integer quantity) {
    }

    private record PreparedOrderLine(Long productId, String productNo, Long skuId, String skuNo, String skuName,
                                     String specJson, Long priceCent, Integer quantity, Long subtotalCent,
                                     Long sellerUserId) {
    }
}
