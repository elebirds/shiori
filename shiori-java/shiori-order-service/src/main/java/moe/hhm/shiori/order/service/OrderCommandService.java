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
import moe.hhm.shiori.order.client.ProductSpecItemSnapshot;
import moe.hhm.shiori.order.client.ProductServiceClient;
import moe.hhm.shiori.order.client.ProductSkuSnapshot;
import moe.hhm.shiori.order.client.UserAddressSnapshot;
import moe.hhm.shiori.order.client.UserServiceClient;
import moe.hhm.shiori.order.client.NotifyChatClient;
import moe.hhm.shiori.order.client.ChatConversationSnapshot;
import moe.hhm.shiori.order.client.PaymentServiceClient;
import moe.hhm.shiori.order.client.ReleaseBalancePaymentSnapshot;
import moe.hhm.shiori.order.client.ReserveBalancePaymentSnapshot;
import moe.hhm.shiori.order.config.OrderMqProperties;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.domain.OrderPaymentMode;
import moe.hhm.shiori.order.domain.OrderRefundStatus;
import moe.hhm.shiori.order.domain.OrderStatus;
import moe.hhm.shiori.order.domain.OutboxStatus;
import moe.hhm.shiori.order.dto.CreateOrderItem;
import moe.hhm.shiori.order.dto.CreateOrderRequest;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.dto.v2.ChatToOrderClickRequest;
import moe.hhm.shiori.order.dto.v2.UpdateOrderFulfillmentRequest;
import moe.hhm.shiori.order.event.EventEnvelope;
import moe.hhm.shiori.order.event.OrderCanceledPayload;
import moe.hhm.shiori.order.event.OrderCreatedPayload;
import moe.hhm.shiori.order.event.OrderDeliveredPayload;
import moe.hhm.shiori.order.event.OrderFinishedPayload;
import moe.hhm.shiori.order.event.OrderPaidPayload;
import moe.hhm.shiori.order.event.OrderTimeoutPayload;
import moe.hhm.shiori.order.model.OrderEntity;
import moe.hhm.shiori.order.model.OrderItemEntity;
import moe.hhm.shiori.order.model.OrderItemRecord;
import moe.hhm.shiori.order.model.OrderOperateIdempotencyRecord;
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
    private static final String EVENT_ORDER_DELIVERED = "OrderDelivered";
    private static final String EVENT_ORDER_FINISHED = "OrderFinished";
    private static final String SOURCE_BUYER = "BUYER";
    private static final String SOURCE_SELLER = "SELLER";
    private static final String SOURCE_ADMIN = "ADMIN";
    private static final String SOURCE_SYSTEM = "SYSTEM";
    private static final String SOURCE_CHAT = "CHAT";
    private static final String OP_CREATE = "CREATE";
    private static final String OP_PAY = "PAY";
    private static final String OP_CANCEL = "CANCEL";

    private final OrderMapper orderMapper;
    private final ProductServiceClient productServiceClient;
    private final UserServiceClient userServiceClient;
    private final NotifyChatClient notifyChatClient;
    private final PaymentServiceClient paymentServiceClient;
    private final OrderProperties orderProperties;
    private final OrderMqProperties orderMqProperties;
    private final ObjectMapper objectMapper;
    private final OrderMetrics orderMetrics;

    public OrderCommandService(OrderMapper orderMapper,
                               ProductServiceClient productServiceClient,
                               UserServiceClient userServiceClient,
                               NotifyChatClient notifyChatClient,
                               PaymentServiceClient paymentServiceClient,
                               OrderProperties orderProperties,
                               OrderMqProperties orderMqProperties,
                               ObjectMapper objectMapper,
                               OrderMetrics orderMetrics) {
        this.orderMapper = orderMapper;
        this.productServiceClient = productServiceClient;
        this.userServiceClient = userServiceClient;
        this.notifyChatClient = notifyChatClient;
        this.paymentServiceClient = paymentServiceClient;
        this.orderProperties = orderProperties;
        this.orderMqProperties = orderMqProperties;
        this.objectMapper = objectMapper;
        this.orderMetrics = orderMetrics;
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateOrderResponse createOrder(Long buyerUserId,
                                           List<String> roles,
                                           String idempotencyKey,
                                           CreateOrderRequest request) {
        String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);

        String existedOrderNo = orderMapper.findOrderNoByBuyerAndIdempotencyKey(buyerUserId, normalizedIdempotencyKey);
        if (StringUtils.hasText(existedOrderNo)) {
            orderMetrics.incIdempotency(OP_CREATE, "hit");
            return buildIdempotentCreateResponse(existedOrderNo);
        }
        orderMetrics.incIdempotency(OP_CREATE, "miss");

        String normalizedSource = normalizeSource(request == null ? null : request.source());
        Long chatConversationId = normalizeConversationId(request == null ? null : request.conversationId(), normalizedSource);

        List<PreparedOrderLine> lines = prepareOrderLines(buyerUserId, roles, request);
        validateChatSourceContext(buyerUserId, roles, normalizedSource, chatConversationId, lines);
        long totalAmountCent = lines.stream().mapToLong(PreparedOrderLine::subtotalCent).sum();
        int itemCount = lines.stream().mapToInt(PreparedOrderLine::quantity).sum();
        boolean allowMeetup = lines.stream().allMatch(PreparedOrderLine::allowMeetup);
        boolean allowDelivery = lines.stream().allMatch(PreparedOrderLine::allowDelivery);
        if (!allowMeetup && !allowDelivery) {
            throw new BizException(OrderErrorCode.ORDER_FULFILLMENT_INVALID, HttpStatus.BAD_REQUEST);
        }
        String initialFulfillmentMode = null;
        if (allowMeetup && !allowDelivery) {
            initialFulfillmentMode = "MEETUP";
        } else if (!allowMeetup) {
            initialFulfillmentMode = "DELIVERY";
        }
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
            orderMapper.insertCreateIdempotency(buyerUserId, normalizedIdempotencyKey, orderNo);
        } catch (DuplicateKeyException ex) {
            compensateReleased(orderNo, buyerUserId, roles, deducted);
            orderMetrics.incIdempotency(OP_CREATE, "hit");
            return buildIdempotentCreateResponseFromKey(buyerUserId, normalizedIdempotencyKey);
        }

        try {
            Long chatListingId = SOURCE_CHAT.equals(normalizedSource) ? lines.getFirst().productId() : null;
            persistOrder(
                    orderNo,
                    buyerUserId,
                    lines.getFirst().sellerUserId(),
                    totalAmountCent,
                    itemCount,
                    timeoutAt,
                    normalizedSource,
                    chatConversationId,
                    chatListingId,
                    allowMeetup,
                    allowDelivery,
                    initialFulfillmentMode,
                    lines
            );
            appendOrderCreatedOutbox(orderNo, buyerUserId, lines.getFirst().sellerUserId(), totalAmountCent, itemCount);
            appendOrderTimeoutOutbox(orderNo, buyerUserId);
            orderMetrics.incStateTransition("NEW", OrderStatus.UNPAID.name(), SOURCE_BUYER);
            if (SOURCE_CHAT.equals(normalizedSource) && chatConversationId != null) {
                orderMetrics.incChatToOrderSubmit(normalizedSource);
                orderMetrics.incChatTradeStatusCardSent("ORDER_CREATED");
            }
            return new CreateOrderResponse(orderNo, OrderStatus.UNPAID.name(), totalAmountCent, itemCount, false);
        } catch (RuntimeException ex) {
            compensateReleased(orderNo, buyerUserId, roles, deducted);
            throw ex;
        }
    }

    public void recordChatToOrderClick(Long userId, ChatToOrderClickRequest request) {
        if (userId == null || userId <= 0 || request == null) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        String normalizedSource = normalizeSource(request.source());
        normalizeConversationId(request.conversationId(), normalizedSource);
        if (request.listingId() == null || request.listingId() <= 0) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        orderMetrics.incChatToOrderClick(normalizedSource);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse updateOrderFulfillmentByBuyer(Long buyerUserId,
                                                              List<String> roles,
                                                              String orderNo,
                                                              UpdateOrderFulfillmentRequest request) {
        OrderRecord order = requireOrder(orderNo);
        ensureBuyer(order, buyerUserId);
        if (OrderStatus.fromCode(order.status()) != OrderStatus.UNPAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        if (request == null || !StringUtils.hasText(request.fulfillmentMode())) {
            throw new BizException(OrderErrorCode.ORDER_FULFILLMENT_INVALID, HttpStatus.BAD_REQUEST);
        }
        String mode = request.fulfillmentMode().trim().toUpperCase();
        if ("MEETUP".equals(mode)) {
            if (!isAllowMeetup(order)) {
                throw new BizException(OrderErrorCode.ORDER_FULFILLMENT_INVALID, HttpStatus.BAD_REQUEST);
            }
            int affected = orderMapper.updateOrderFulfillmentToMeetup(
                    orderNo,
                    buyerUserId,
                    OrderStatus.UNPAID.getCode()
            );
            if (affected == 0) {
                throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
            }
            return new OrderOperateResponse(orderNo, OrderStatus.UNPAID.name(), false);
        }
        if ("DELIVERY".equals(mode)) {
            if (!isAllowDelivery(order)) {
                throw new BizException(OrderErrorCode.ORDER_FULFILLMENT_INVALID, HttpStatus.BAD_REQUEST);
            }
            if (request.addressId() == null || request.addressId() <= 0) {
                throw new BizException(OrderErrorCode.ORDER_SHIPPING_ADDRESS_REQUIRED, HttpStatus.BAD_REQUEST);
            }
            UserAddressSnapshot address = userServiceClient.getMyAddress(request.addressId(), buyerUserId, roles);
            if (address == null || !buyerUserId.equals(address.userId())) {
                throw new BizException(OrderErrorCode.ORDER_ADDRESS_NOT_FOUND, HttpStatus.BAD_REQUEST);
            }
            int affected = orderMapper.updateOrderFulfillmentToDelivery(
                    orderNo,
                    buyerUserId,
                    OrderStatus.UNPAID.getCode(),
                    address.addressId(),
                    address.receiverName(),
                    address.receiverPhone(),
                    address.province(),
                    address.city(),
                    address.district(),
                    address.detailAddress()
            );
            if (affected == 0) {
                throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
            }
            return new OrderOperateResponse(orderNo, OrderStatus.UNPAID.name(), false);
        }
        throw new BizException(OrderErrorCode.ORDER_FULFILLMENT_INVALID, HttpStatus.BAD_REQUEST);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse payOrderByBalance(Long buyerUserId, String orderNo, String idempotencyKey) {
        String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);
        OrderRecord order = requireOrder(orderNo);
        ensureBuyer(order, buyerUserId);

        if (hasOperateIdempotencyHit(buyerUserId, OP_PAY, normalizedIdempotencyKey, orderNo)) {
            OrderStatus status = OrderStatus.fromCode(order.status());
            if (status == OrderStatus.PAID && isBalanceEscrowOrder(orderNo)) {
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        OrderStatus status = OrderStatus.fromCode(order.status());
        if (status == OrderStatus.PAID) {
            if (isBalanceEscrowOrder(orderNo)) {
                saveOperateIdempotency(buyerUserId, OP_PAY, normalizedIdempotencyKey, orderNo);
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_PAYMENT_CONFLICT, HttpStatus.CONFLICT);
        }
        if (status != OrderStatus.UNPAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        ensureFulfillmentReadyForPay(order);

        ReserveBalancePaymentSnapshot reserved = paymentServiceClient.reserveOrderPayment(
                orderNo,
                order.buyerUserId(),
                order.sellerUserId(),
                order.totalAmountCent(),
                buyerUserId,
                List.of("ROLE_USER")
        );
        String paymentNo = reserved.paymentNo();
        boolean releaseRequired = true;

        try {
            int affected = orderMapper.markOrderPaidByBalance(
                    orderNo,
                    buyerUserId,
                    paymentNo,
                    LocalDateTime.now(),
                    OrderStatus.UNPAID.getCode(),
                    OrderStatus.PAID.getCode()
            );
            if (affected == 0) {
                OrderRecord latest = requireOrder(orderNo);
                if (OrderStatus.fromCode(latest.status()) == OrderStatus.PAID
                        && Objects.equals(latest.paymentNo(), paymentNo)
                        && isBalanceEscrowOrder(orderNo)) {
                    releaseRequired = false;
                    saveOperateIdempotency(buyerUserId, OP_PAY, normalizedIdempotencyKey, orderNo);
                    return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
                }
                throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
            }
        } catch (DuplicateKeyException ex) {
            OrderRecord paymentOrder = orderMapper.findOrderByPaymentNo(paymentNo);
            if (paymentOrder != null && orderNo.equals(paymentOrder.orderNo()) && isBalanceEscrowOrder(orderNo)) {
                releaseRequired = false;
                saveOperateIdempotency(buyerUserId, OP_PAY, normalizedIdempotencyKey, orderNo);
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            orderMetrics.incIdempotency(OP_PAY, "conflict");
            throw new BizException(OrderErrorCode.ORDER_PAYMENT_CONFLICT, HttpStatus.CONFLICT);
        } catch (RuntimeException ex) {
            if (releaseRequired) {
                safeReleaseReservedPayment(orderNo, buyerUserId, "order_pay_failed:" + ex.getClass().getSimpleName());
            }
            throw ex;
        }

        releaseRequired = false;
        try {
            appendOrderPaidOutbox(orderNo, paymentNo, order.buyerUserId(), order.sellerUserId(), order.totalAmountCent());
            orderMetrics.incStateTransition(OrderStatus.UNPAID.name(), OrderStatus.PAID.name(), SOURCE_BUYER);
            insertStatusAudit(orderNo, buyerUserId, SOURCE_BUYER, OrderStatus.UNPAID, OrderStatus.PAID,
                    "买家余额支付成功, paymentNo=" + paymentNo);
            recordTradeStatusCardSentForChatOrder(order, "ORDER_PAID");
            saveOperateIdempotency(buyerUserId, OP_PAY, normalizedIdempotencyKey, orderNo);
            return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), false);
        } catch (RuntimeException ex) {
            safeReleaseReservedPayment(orderNo, buyerUserId, "order_pay_tx_rollback:" + ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse payOrder(Long buyerUserId, String orderNo, String paymentNo, String idempotencyKey) {
        if (!StringUtils.hasText(paymentNo)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);

        OrderRecord order = requireOrder(orderNo);
        ensureBuyer(order, buyerUserId);
        if (hasOperateIdempotencyHit(buyerUserId, OP_PAY, normalizedIdempotencyKey, orderNo)) {
            OrderStatus hitStatus = OrderStatus.fromCode(order.status());
            if (hitStatus == OrderStatus.PAID && Objects.equals(order.paymentNo(), paymentNo)) {
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            if (hitStatus == OrderStatus.PAID) {
                throw new BizException(OrderErrorCode.ORDER_PAYMENT_CONFLICT, HttpStatus.CONFLICT);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        OrderStatus status = OrderStatus.fromCode(order.status());
        if (status == OrderStatus.PAID) {
            if (Objects.equals(order.paymentNo(), paymentNo)) {
                saveOperateIdempotency(buyerUserId, OP_PAY, normalizedIdempotencyKey, orderNo);
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            orderMetrics.incIdempotency(OP_PAY, "conflict");
            throw new BizException(OrderErrorCode.ORDER_PAYMENT_CONFLICT, HttpStatus.CONFLICT);
        }
        if (status != OrderStatus.UNPAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        ensureFulfillmentReadyForPay(order);

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
                saveOperateIdempotency(buyerUserId, OP_PAY, normalizedIdempotencyKey, orderNo);
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            orderMetrics.incIdempotency(OP_PAY, "conflict");
            throw new BizException(OrderErrorCode.ORDER_PAYMENT_CONFLICT, HttpStatus.CONFLICT);
        }

        if (affected == 0) {
            OrderRecord latest = requireOrder(orderNo);
            if (OrderStatus.fromCode(latest.status()) == OrderStatus.PAID
                    && Objects.equals(latest.paymentNo(), paymentNo)) {
                saveOperateIdempotency(buyerUserId, OP_PAY, normalizedIdempotencyKey, orderNo);
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        appendOrderPaidOutbox(orderNo, paymentNo, order.buyerUserId(), order.sellerUserId(), order.totalAmountCent());
        orderMetrics.incStateTransition(OrderStatus.UNPAID.name(), OrderStatus.PAID.name(), SOURCE_BUYER);
        insertStatusAudit(orderNo, buyerUserId, SOURCE_BUYER, OrderStatus.UNPAID, OrderStatus.PAID,
                "买家支付成功, paymentNo=" + paymentNo);
        recordTradeStatusCardSentForChatOrder(order, "ORDER_PAID");
        saveOperateIdempotency(buyerUserId, OP_PAY, normalizedIdempotencyKey, orderNo);
        return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse cancelOrder(Long buyerUserId,
                                            List<String> roles,
                                            String orderNo,
                                            String reason,
                                            String idempotencyKey) {
        String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);
        OrderRecord order = requireOrder(orderNo);
        ensureBuyer(order, buyerUserId);
        if (hasOperateIdempotencyHit(buyerUserId, OP_CANCEL, normalizedIdempotencyKey, orderNo)) {
            if (OrderStatus.fromCode(order.status()) == OrderStatus.CANCELED) {
                return new OrderOperateResponse(orderNo, OrderStatus.CANCELED.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        OrderStatus status = OrderStatus.fromCode(order.status());
        if (status == OrderStatus.CANCELED) {
            saveOperateIdempotency(buyerUserId, OP_CANCEL, normalizedIdempotencyKey, orderNo);
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
                saveOperateIdempotency(buyerUserId, OP_CANCEL, normalizedIdempotencyKey, orderNo);
                return new OrderOperateResponse(orderNo, OrderStatus.CANCELED.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        List<OrderItemRecord> items = orderMapper.listOrderItemsByOrderNo(orderNo);
        releaseOrderItems(orderNo, buyerUserId, roles, items);
        appendOrderCanceledOutbox(orderNo, order.buyerUserId(), order.sellerUserId(), resolveCancelReason(reason));
        orderMetrics.incStateTransition(OrderStatus.UNPAID.name(), OrderStatus.CANCELED.name(), SOURCE_BUYER);
        insertStatusAudit(orderNo, buyerUserId, SOURCE_BUYER, OrderStatus.UNPAID, OrderStatus.CANCELED,
                resolveCancelReason(reason));
        saveOperateIdempotency(buyerUserId, OP_CANCEL, normalizedIdempotencyKey, orderNo);
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
        orderMetrics.incStateTransition(OrderStatus.UNPAID.name(), OrderStatus.CANCELED.name(), SOURCE_ADMIN);
        insertStatusAudit(orderNo, operatorUserId, SOURCE_ADMIN, OrderStatus.UNPAID, OrderStatus.CANCELED,
                normalizedReason);
        OrderRecord after = requireOrder(orderNo);
        insertAdminAudit(operatorUserId, orderNo, "ORDER_ADMIN_CANCEL", before, after, normalizedReason);
        return new OrderOperateResponse(orderNo, OrderStatus.CANCELED.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse deliverOrderAsSeller(Long sellerUserId, String orderNo, String reason) {
        OrderRecord order = requireOrder(orderNo);
        ensureSeller(order, sellerUserId);
        ensureOrderNotRefunded(order);
        ensureShippingAddressReadyForDelivery(order);
        String normalizedReason = resolveDeliverReason(reason, false);

        OrderStatus status = OrderStatus.fromCode(order.status());
        if (status == OrderStatus.DELIVERING) {
            return new OrderOperateResponse(orderNo, OrderStatus.DELIVERING.name(), true);
        }
        if (status != OrderStatus.PAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        int affected = orderMapper.markOrderDeliveringBySeller(
                orderNo,
                sellerUserId,
                OrderStatus.PAID.getCode(),
                OrderStatus.DELIVERING.getCode()
        );
        if (affected == 0) {
            OrderRecord latest = requireOrder(orderNo);
            if (OrderStatus.fromCode(latest.status()) == OrderStatus.DELIVERING) {
                return new OrderOperateResponse(orderNo, OrderStatus.DELIVERING.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        orderMetrics.incStateTransition(OrderStatus.PAID.name(), OrderStatus.DELIVERING.name(), SOURCE_SELLER);
        insertStatusAudit(orderNo, sellerUserId, SOURCE_SELLER, OrderStatus.PAID, OrderStatus.DELIVERING, normalizedReason);
        recordTradeStatusCardSentForChatOrder(order, "ORDER_DELIVERED");
        appendOrderDeliveredOutbox(orderNo, order.buyerUserId(), order.sellerUserId(), SOURCE_SELLER, sellerUserId);
        return new OrderOperateResponse(orderNo, OrderStatus.DELIVERING.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse finishOrderAsSeller(Long sellerUserId, String orderNo, String reason) {
        OrderRecord order = requireOrder(orderNo);
        ensureSeller(order, sellerUserId);
        ensureOrderNotRefunded(order);
        String normalizedReason = resolveFinishReason(reason, false);

        OrderStatus status = OrderStatus.fromCode(order.status());
        if (status == OrderStatus.FINISHED) {
            return new OrderOperateResponse(orderNo, OrderStatus.FINISHED.name(), true);
        }
        if (status != OrderStatus.DELIVERING) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        int affected = orderMapper.markOrderFinishedBySeller(
                orderNo,
                sellerUserId,
                OrderStatus.DELIVERING.getCode(),
                OrderStatus.FINISHED.getCode()
        );
        if (affected == 0) {
            OrderRecord latest = requireOrder(orderNo);
            if (OrderStatus.fromCode(latest.status()) == OrderStatus.FINISHED) {
                return new OrderOperateResponse(orderNo, OrderStatus.FINISHED.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        settleBalanceEscrowIfNeeded(orderNo, order, SOURCE_SELLER, sellerUserId);
        orderMetrics.incStateTransition(OrderStatus.DELIVERING.name(), OrderStatus.FINISHED.name(), SOURCE_SELLER);
        insertStatusAudit(orderNo, sellerUserId, SOURCE_SELLER, OrderStatus.DELIVERING, OrderStatus.FINISHED, normalizedReason);
        appendOrderFinishedOutbox(orderNo, order.buyerUserId(), order.sellerUserId(), SOURCE_SELLER, sellerUserId);
        return new OrderOperateResponse(orderNo, OrderStatus.FINISHED.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse deliverOrderAsAdmin(Long operatorUserId, String orderNo, String reason) {
        OrderRecord before = requireOrder(orderNo);
        ensureOrderNotRefunded(before);
        ensureShippingAddressReadyForDelivery(before);
        String normalizedReason = resolveDeliverReason(reason, true);

        OrderStatus status = OrderStatus.fromCode(before.status());
        if (status == OrderStatus.DELIVERING) {
            insertAdminAudit(operatorUserId, orderNo, "ORDER_ADMIN_DELIVER", before, before, normalizedReason);
            return new OrderOperateResponse(orderNo, OrderStatus.DELIVERING.name(), true);
        }
        if (status != OrderStatus.PAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        int affected = orderMapper.markOrderDeliveringAsAdmin(
                orderNo,
                OrderStatus.PAID.getCode(),
                OrderStatus.DELIVERING.getCode()
        );
        if (affected == 0) {
            OrderRecord latest = requireOrder(orderNo);
            if (OrderStatus.fromCode(latest.status()) == OrderStatus.DELIVERING) {
                insertAdminAudit(operatorUserId, orderNo, "ORDER_ADMIN_DELIVER", before, latest, normalizedReason);
                return new OrderOperateResponse(orderNo, OrderStatus.DELIVERING.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        orderMetrics.incStateTransition(OrderStatus.PAID.name(), OrderStatus.DELIVERING.name(), SOURCE_ADMIN);
        insertStatusAudit(orderNo, operatorUserId, SOURCE_ADMIN, OrderStatus.PAID, OrderStatus.DELIVERING, normalizedReason);
        recordTradeStatusCardSentForChatOrder(before, "ORDER_DELIVERED");
        appendOrderDeliveredOutbox(orderNo, before.buyerUserId(), before.sellerUserId(), SOURCE_ADMIN, operatorUserId);
        OrderRecord after = requireOrder(orderNo);
        insertAdminAudit(operatorUserId, orderNo, "ORDER_ADMIN_DELIVER", before, after, normalizedReason);
        return new OrderOperateResponse(orderNo, OrderStatus.DELIVERING.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse finishOrderAsAdmin(Long operatorUserId, String orderNo, String reason) {
        OrderRecord before = requireOrder(orderNo);
        ensureOrderNotRefunded(before);
        String normalizedReason = resolveFinishReason(reason, true);

        OrderStatus status = OrderStatus.fromCode(before.status());
        if (status == OrderStatus.FINISHED) {
            insertAdminAudit(operatorUserId, orderNo, "ORDER_ADMIN_FINISH", before, before, normalizedReason);
            return new OrderOperateResponse(orderNo, OrderStatus.FINISHED.name(), true);
        }
        if (status != OrderStatus.DELIVERING) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        int affected = orderMapper.markOrderFinishedAsAdmin(
                orderNo,
                OrderStatus.DELIVERING.getCode(),
                OrderStatus.FINISHED.getCode()
        );
        if (affected == 0) {
            OrderRecord latest = requireOrder(orderNo);
            if (OrderStatus.fromCode(latest.status()) == OrderStatus.FINISHED) {
                insertAdminAudit(operatorUserId, orderNo, "ORDER_ADMIN_FINISH", before, latest, normalizedReason);
                return new OrderOperateResponse(orderNo, OrderStatus.FINISHED.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        settleBalanceEscrowIfNeeded(orderNo, before, SOURCE_ADMIN, operatorUserId);
        orderMetrics.incStateTransition(OrderStatus.DELIVERING.name(), OrderStatus.FINISHED.name(), SOURCE_ADMIN);
        insertStatusAudit(orderNo, operatorUserId, SOURCE_ADMIN, OrderStatus.DELIVERING, OrderStatus.FINISHED, normalizedReason);
        appendOrderFinishedOutbox(orderNo, before.buyerUserId(), before.sellerUserId(), SOURCE_ADMIN, operatorUserId);
        OrderRecord after = requireOrder(orderNo);
        insertAdminAudit(operatorUserId, orderNo, "ORDER_ADMIN_FINISH", before, after, normalizedReason);
        return new OrderOperateResponse(orderNo, OrderStatus.FINISHED.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderOperateResponse confirmReceiptAsBuyer(Long buyerUserId, String orderNo, String reason) {
        OrderRecord order = requireOrder(orderNo);
        ensureBuyer(order, buyerUserId);
        ensureOrderNotRefunded(order);
        String normalizedReason = resolveConfirmReceiptReason(reason);

        OrderStatus status = OrderStatus.fromCode(order.status());
        if (status == OrderStatus.FINISHED) {
            return new OrderOperateResponse(orderNo, OrderStatus.FINISHED.name(), true);
        }
        if (status != OrderStatus.DELIVERING) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        int affected = orderMapper.markOrderFinishedByBuyer(
                orderNo,
                buyerUserId,
                OrderStatus.DELIVERING.getCode(),
                OrderStatus.FINISHED.getCode()
        );
        if (affected == 0) {
            OrderRecord latest = requireOrder(orderNo);
            if (OrderStatus.fromCode(latest.status()) == OrderStatus.FINISHED) {
                return new OrderOperateResponse(orderNo, OrderStatus.FINISHED.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        settleBalanceEscrowIfNeeded(orderNo, order, SOURCE_BUYER, buyerUserId);
        orderMetrics.incStateTransition(OrderStatus.DELIVERING.name(), OrderStatus.FINISHED.name(), SOURCE_BUYER);
        insertStatusAudit(orderNo, buyerUserId, SOURCE_BUYER, OrderStatus.DELIVERING, OrderStatus.FINISHED, normalizedReason);
        recordTradeStatusCardSentForChatOrder(order, "ORDER_FINISHED");
        appendOrderFinishedOutbox(orderNo, order.buyerUserId(), order.sellerUserId(), SOURCE_BUYER, buyerUserId);
        return new OrderOperateResponse(orderNo, OrderStatus.FINISHED.name(), false);
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
        orderMetrics.incStateTransition(OrderStatus.UNPAID.name(), OrderStatus.CANCELED.name(), SOURCE_SYSTEM);
        insertStatusAudit(orderNo, null, SOURCE_SYSTEM, OrderStatus.UNPAID, OrderStatus.CANCELED, "超时未支付自动取消");
    }

    private void persistOrder(String orderNo,
                              Long buyerUserId,
                              Long sellerUserId,
                              long totalAmountCent,
                              int itemCount,
                              LocalDateTime timeoutAt,
                              String source,
                              Long conversationId,
                              Long listingId,
                              boolean allowMeetup,
                              boolean allowDelivery,
                              String fulfillmentMode,
                              List<PreparedOrderLine> lines) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderNo(orderNo);
        orderEntity.setBuyerUserId(buyerUserId);
        orderEntity.setSellerUserId(sellerUserId);
        orderEntity.setStatus(OrderStatus.UNPAID.getCode());
        orderEntity.setTotalAmountCent(totalAmountCent);
        orderEntity.setItemCount(itemCount);
        orderEntity.setTimeoutAt(timeoutAt);
        orderEntity.setBizSource(source);
        orderEntity.setChatConversationId(conversationId);
        orderEntity.setChatListingId(listingId);
        orderEntity.setAllowMeetup(allowMeetup ? 1 : 0);
        orderEntity.setAllowDelivery(allowDelivery ? 1 : 0);
        orderEntity.setFulfillmentMode(fulfillmentMode);
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
            String skuName = resolveSkuName(sku);
            String specJson = resolveSpecJson(sku);
            TradeCapability capability = resolveTradeCapability(product.tradeMode());

            lines.add(new PreparedOrderLine(
                    item.productId(),
                    product.productNo(),
                    item.skuId(),
                    sku.skuNo(),
                    skuName,
                    specJson,
                    sku.priceCent(),
                    item.quantity(),
                    subtotal,
                    product.ownerUserId(),
                    capability.allowMeetup(),
                    capability.allowDelivery()
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

    private String resolveSkuName(ProductSkuSnapshot sku) {
        if (StringUtils.hasText(sku.displayName())) {
            return sku.displayName().trim();
        }
        if (sku.specItems() != null && !sku.specItems().isEmpty()) {
            return sku.specItems().stream()
                    .filter(item -> item != null && StringUtils.hasText(item.name()) && StringUtils.hasText(item.value()))
                    .map(item -> item.name().trim() + ":" + item.value().trim())
                    .reduce((left, right) -> left + " / " + right)
                    .orElseGet(() -> StringUtils.hasText(sku.skuNo()) ? sku.skuNo() : "默认规格");
        }
        if (StringUtils.hasText(sku.skuNo())) {
            return sku.skuNo();
        }
        return "默认规格";
    }

    private String resolveSpecJson(ProductSkuSnapshot sku) {
        if (StringUtils.hasText(sku.legacySpecJson())) {
            return sku.legacySpecJson().trim();
        }
        if (sku.specItems() == null || sku.specItems().isEmpty()) {
            return "{}";
        }
        Map<String, String> spec = new LinkedHashMap<>();
        for (ProductSpecItemSnapshot item : sku.specItems()) {
            if (item == null || !StringUtils.hasText(item.name()) || !StringUtils.hasText(item.value())) {
                continue;
            }
            spec.put(item.name().trim(), item.value().trim());
        }
        if (spec.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (JacksonException ex) {
            return "{}";
        }
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

    private void appendOrderDeliveredOutbox(String orderNo,
                                            Long buyerUserId,
                                            Long sellerUserId,
                                            String operatorType,
                                            Long operatorUserId) {
        OrderDeliveredPayload payload = new OrderDeliveredPayload(
                orderNo,
                buyerUserId,
                sellerUserId,
                operatorType,
                operatorUserId,
                Instant.now()
        );
        appendOutbox(orderNo, EVENT_ORDER_DELIVERED, payload, orderMqProperties.getEventExchange(),
                orderMqProperties.getOrderDeliveredRoutingKey());
    }

    private void appendOrderFinishedOutbox(String orderNo,
                                           Long buyerUserId,
                                           Long sellerUserId,
                                           String operatorType,
                                           Long operatorUserId) {
        OrderFinishedPayload payload = new OrderFinishedPayload(
                orderNo,
                buyerUserId,
                sellerUserId,
                operatorType,
                operatorUserId,
                Instant.now()
        );
        appendOutbox(orderNo, EVENT_ORDER_FINISHED, payload, orderMqProperties.getEventExchange(),
                orderMqProperties.getOrderFinishedRoutingKey());
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

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        String normalized = source.trim().toUpperCase();
        if (!SOURCE_CHAT.equals(normalized)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private Long normalizeConversationId(Long conversationId, String source) {
        if (conversationId == null) {
            if (SOURCE_CHAT.equals(source)) {
                throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        if (conversationId <= 0 || !SOURCE_CHAT.equals(source)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        return conversationId;
    }

    private void recordTradeStatusCardSentForChatOrder(OrderRecord order, String status) {
        if (order == null || !SOURCE_CHAT.equals(order.bizSource())) {
            return;
        }
        if (order.chatConversationId() == null || order.chatConversationId() <= 0) {
            return;
        }
        orderMetrics.incChatTradeStatusCardSent(status);
    }

    private void validateChatSourceContext(Long buyerUserId,
                                           List<String> roles,
                                           String source,
                                           Long conversationId,
                                           List<PreparedOrderLine> lines) {
        if (!SOURCE_CHAT.equals(source)) {
            return;
        }
        if (conversationId == null || conversationId <= 0 || lines == null || lines.isEmpty()) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        PreparedOrderLine first = lines.getFirst();
        Long expectedSellerId = first.sellerUserId();
        ChatConversationSnapshot conversation = notifyChatClient.getConversationForUser(conversationId, buyerUserId, roles);
        if (conversation == null
                || !buyerUserId.equals(conversation.buyerId())
                || !expectedSellerId.equals(conversation.sellerId())) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveCancelReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : "用户主动取消";
    }

    private String resolveDeliverReason(String reason, boolean adminSource) {
        if (StringUtils.hasText(reason)) {
            return reason.trim();
        }
        return adminSource ? "管理员标记发货" : "卖家标记发货";
    }

    private String resolveFinishReason(String reason, boolean adminSource) {
        if (StringUtils.hasText(reason)) {
            return reason.trim();
        }
        return adminSource ? "管理员标记完成" : "卖家标记完成";
    }

    private String resolveConfirmReceiptReason(String reason) {
        if (StringUtils.hasText(reason)) {
            return reason.trim();
        }
        return "买家确认收货";
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

    private void settleBalanceEscrowIfNeeded(String orderNo,
                                             OrderRecord order,
                                             String operatorType,
                                             Long operatorUserId) {
        if (order == null || !isBalanceEscrowOrder(orderNo)) {
            return;
        }
        if (OrderRefundStatus.SUCCEEDED == OrderRefundStatus.fromCode(order.refundStatus())) {
            return;
        }
        paymentServiceClient.settleOrderPayment(
                orderNo,
                operatorType,
                operatorUserId,
                operatorUserId == null ? order.buyerUserId() : operatorUserId,
                List.of("ROLE_USER")
        );
    }

    private void safeReleaseReservedPayment(String orderNo, Long userId, String reason) {
        try {
            ReleaseBalancePaymentSnapshot released = paymentServiceClient.releaseOrderPayment(
                    orderNo,
                    reason,
                    userId,
                    List.of("ROLE_USER")
            );
            log.warn("订单支付补偿释放执行, orderNo={}, status={}, idempotent={}",
                    orderNo, released.status(), released.idempotent());
        } catch (RuntimeException ex) {
            log.error("订单支付补偿释放失败, orderNo={}, err={}", orderNo, ex.getMessage());
        }
    }

    private boolean isBalanceEscrowOrder(String orderNo) {
        String mode = orderMapper.findPaymentModeByOrderNo(orderNo);
        return OrderPaymentMode.fromCode(mode) == OrderPaymentMode.BALANCE_ESCROW;
    }

    private void ensureBuyer(OrderRecord order, Long buyerUserId) {
        if (!order.buyerUserId().equals(buyerUserId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    private void ensureSeller(OrderRecord order, Long sellerUserId) {
        if (!order.sellerUserId().equals(sellerUserId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    private void ensureOrderNotRefunded(OrderRecord order) {
        if (order == null) {
            return;
        }
        if (OrderRefundStatus.SUCCEEDED == OrderRefundStatus.fromCode(order.refundStatus())) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }
    }

    private void ensureFulfillmentReadyForPay(OrderRecord order) {
        if (order == null) {
            return;
        }
        boolean allowMeetup = isAllowMeetup(order);
        boolean allowDelivery = isAllowDelivery(order);
        String mode = normalizeFulfillmentMode(order.fulfillmentMode());

        if (allowMeetup && allowDelivery) {
            if (mode == null) {
                throw new BizException(OrderErrorCode.ORDER_FULFILLMENT_NOT_SELECTED, HttpStatus.BAD_REQUEST);
            }
            if ("DELIVERY".equals(mode) && !hasShippingSnapshot(order)) {
                throw new BizException(OrderErrorCode.ORDER_SHIPPING_ADDRESS_REQUIRED, HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (allowDelivery) {
            if (!"DELIVERY".equals(mode) || !hasShippingSnapshot(order)) {
                throw new BizException(OrderErrorCode.ORDER_SHIPPING_ADDRESS_REQUIRED, HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (allowMeetup && mode == null) {
            // 兼容历史订单，面交场景允许空值。
            return;
        }
        if (allowMeetup && !"MEETUP".equals(mode)) {
            throw new BizException(OrderErrorCode.ORDER_FULFILLMENT_INVALID, HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureShippingAddressReadyForDelivery(OrderRecord order) {
        if (order == null) {
            return;
        }
        if (!"DELIVERY".equals(normalizeFulfillmentMode(order.fulfillmentMode()))) {
            return;
        }
        if (!hasShippingSnapshot(order)) {
            throw new BizException(OrderErrorCode.ORDER_SHIPPING_ADDRESS_REQUIRED, HttpStatus.BAD_REQUEST);
        }
    }

    private boolean hasShippingSnapshot(OrderRecord order) {
        return order != null
                && order.shippingAddressId() != null
                && order.shippingAddressId() > 0
                && StringUtils.hasText(order.shippingReceiverName())
                && StringUtils.hasText(order.shippingReceiverPhone())
                && StringUtils.hasText(order.shippingProvince())
                && StringUtils.hasText(order.shippingCity())
                && StringUtils.hasText(order.shippingDistrict())
                && StringUtils.hasText(order.shippingDetailAddress());
    }

    private boolean isAllowMeetup(OrderRecord order) {
        return order != null && order.allowMeetup() != null && order.allowMeetup() == 1;
    }

    private boolean isAllowDelivery(OrderRecord order) {
        return order != null && order.allowDelivery() != null && order.allowDelivery() == 1;
    }

    private String normalizeFulfillmentMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String mode = raw.trim().toUpperCase();
        if ("MEETUP".equals(mode) || "DELIVERY".equals(mode)) {
            return mode;
        }
        return null;
    }

    private TradeCapability resolveTradeCapability(String tradeMode) {
        if (!StringUtils.hasText(tradeMode)) {
            return new TradeCapability(true, true);
        }
        String normalized = tradeMode.trim().toUpperCase();
        return switch (normalized) {
            case "MEETUP" -> new TradeCapability(true, false);
            case "DELIVERY" -> new TradeCapability(false, true);
            case "BOTH" -> new TradeCapability(true, true);
            default -> new TradeCapability(true, true);
        };
    }

    private void insertStatusAudit(String orderNo,
                                   Long operatorUserId,
                                   String source,
                                   OrderStatus fromStatus,
                                   OrderStatus toStatus,
                                   String reason) {
        orderMapper.insertStatusAuditLog(
                orderNo,
                operatorUserId,
                source,
                fromStatus.getCode(),
                toStatus.getCode(),
                StringUtils.hasText(reason) ? reason.trim() : null
        );
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        return idempotencyKey.trim();
    }

    private boolean hasOperateIdempotencyHit(Long operatorUserId,
                                             String operationType,
                                             String idempotencyKey,
                                             String orderNo) {
        OrderOperateIdempotencyRecord existed = orderMapper.findOperateIdempotency(operatorUserId, operationType,
                idempotencyKey);
        if (existed == null) {
            orderMetrics.incIdempotency(operationType, "miss");
            return false;
        }
        if (!orderNo.equals(existed.orderNo())) {
            orderMetrics.incIdempotency(operationType, "conflict");
            throw new BizException(OrderErrorCode.ORDER_IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT);
        }
        orderMetrics.incIdempotency(operationType, "hit");
        return true;
    }

    private void saveOperateIdempotency(Long operatorUserId,
                                        String operationType,
                                        String idempotencyKey,
                                        String orderNo) {
        try {
            orderMapper.insertOperateIdempotency(operatorUserId, operationType, idempotencyKey, orderNo);
        } catch (DuplicateKeyException ex) {
            OrderOperateIdempotencyRecord existed =
                    orderMapper.findOperateIdempotency(operatorUserId, operationType, idempotencyKey);
            if (existed != null && orderNo.equals(existed.orderNo())) {
                orderMetrics.incIdempotency(operationType, "hit");
                return;
            }
            orderMetrics.incIdempotency(operationType, "conflict");
            throw new BizException(OrderErrorCode.ORDER_IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT);
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
                                     Long sellerUserId, boolean allowMeetup, boolean allowDelivery) {
    }

    private record TradeCapability(boolean allowMeetup, boolean allowDelivery) {
    }
}
