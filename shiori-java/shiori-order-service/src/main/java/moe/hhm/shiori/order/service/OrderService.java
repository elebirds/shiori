package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.domain.OrderStatus;
import moe.hhm.shiori.order.dto.OrderDetailResponse;
import moe.hhm.shiori.order.dto.OrderItemResponse;
import moe.hhm.shiori.order.dto.OrderPageResponse;
import moe.hhm.shiori.order.dto.OrderShippingAddressResponse;
import moe.hhm.shiori.order.dto.OrderStatusAuditItemResponse;
import moe.hhm.shiori.order.dto.OrderStatusAuditPageResponse;
import moe.hhm.shiori.order.dto.OrderSummaryResponse;
import moe.hhm.shiori.order.dto.v2.OrderTimelineItemResponse;
import moe.hhm.shiori.order.dto.v2.OrderTimelineResponse;
import moe.hhm.shiori.order.dto.v2.SellerOrderPageResponse;
import moe.hhm.shiori.order.dto.v2.SellerOrderSummaryResponse;
import moe.hhm.shiori.order.model.OrderItemRecord;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.model.OrderStatusAuditRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderReviewService orderReviewService;

    public OrderService(OrderMapper orderMapper,
                        OrderReviewService orderReviewService) {
        this.orderMapper = orderMapper;
        this.orderReviewService = orderReviewService;
    }

    public OrderDetailResponse getOrderDetail(Long currentUserId, boolean admin, String orderNo) {
        OrderRecord order = orderMapper.findOrderByOrderNo(orderNo);
        if (order == null || isDeleted(order)) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (!admin && !order.buyerUserId().equals(currentUserId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
        List<OrderItemRecord> itemRecords = orderMapper.listOrderItemsByOrderNo(orderNo);
        List<OrderItemResponse> items = itemRecords.stream()
                .map(item -> new OrderItemResponse(
                        item.productId(),
                        item.productNo(),
                        item.skuId(),
                        item.skuNo(),
                        item.skuName(),
                        item.specJson(),
                        item.priceCent(),
                        item.quantity(),
                        item.subtotalCent()))
                .toList();
        OrderReviewAccessState reviewState = orderReviewService.resolveReviewAccessState(order, currentUserId);
        return new OrderDetailResponse(
                order.orderNo(),
                order.buyerUserId(),
                order.sellerUserId(),
                OrderStatus.fromCode(order.status()).name(),
                order.totalAmountCent(),
                order.bizSource(),
                order.chatConversationId(),
                order.chatListingId(),
                toBool(order.allowMeetup()),
                toBool(order.allowDelivery()),
                order.fulfillmentMode(),
                buildShippingAddress(order),
                order.createdAt(),
                order.paidAt(),
                order.finishedAt(),
                order.timeoutAt(),
                order.refundStatus(),
                order.refundNo(),
                order.refundAmountCent(),
                order.refundUpdatedAt(),
                items,
                reviewState.myReviewSubmitted(),
                reviewState.counterpartyReviewSubmitted(),
                reviewState.canCreateReview(),
                reviewState.canEditReview(),
                reviewState.reviewDeadlineAt()
        );
    }

    public OrderPageResponse listMyOrders(Long buyerUserId, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;

        long total = orderMapper.countOrdersByBuyer(buyerUserId);
        List<OrderRecord> records = orderMapper.listOrdersByBuyer(buyerUserId, normalizedSize, offset);
        List<OrderSummaryResponse> items = records.stream()
                .map(record -> new OrderSummaryResponse(
                        record.orderNo(),
                        OrderStatus.fromCode(record.status()).name(),
                        record.totalAmountCent(),
                        record.itemCount(),
                        record.bizSource(),
                        record.chatConversationId(),
                        record.chatListingId(),
                        record.createdAt(),
                        record.paidAt(),
                        record.refundStatus(),
                        record.refundNo(),
                        record.refundAmountCent(),
                        record.refundUpdatedAt()))
                .toList();
        return new OrderPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public OrderDetailResponse getOrderDetailForAdmin(String orderNo) {
        return getOrderDetail(-1L, true, orderNo);
    }

    public OrderPageResponse listOrdersForAdmin(String orderNo,
                                                String status,
                                                Long buyerUserId,
                                                Long sellerUserId,
                                                int page,
                                                int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        Integer statusCode = parseStatusCode(status);
        String normalizedOrderNo = StringUtils.hasText(orderNo) ? orderNo.trim() : null;

        long total = orderMapper.countOrdersForAdmin(normalizedOrderNo, statusCode, buyerUserId, sellerUserId);
        List<OrderRecord> records = orderMapper.listOrdersForAdmin(
                normalizedOrderNo,
                statusCode,
                buyerUserId,
                sellerUserId,
                normalizedSize,
                offset
        );
        List<OrderSummaryResponse> items = records.stream()
                .map(record -> new OrderSummaryResponse(
                        record.orderNo(),
                        OrderStatus.fromCode(record.status()).name(),
                        record.totalAmountCent(),
                        record.itemCount(),
                        record.bizSource(),
                        record.chatConversationId(),
                        record.chatListingId(),
                        record.createdAt(),
                        record.paidAt(),
                        record.refundStatus(),
                        record.refundNo(),
                        record.refundAmountCent(),
                        record.refundUpdatedAt()))
                .toList();
        return new OrderPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public OrderStatusAuditPageResponse listStatusAuditsForAdmin(String orderNo, int page, int size) {
        requireOrderExists(orderNo);

        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;

        long total = orderMapper.countStatusAuditByOrderNo(orderNo);
        List<OrderStatusAuditRecord> records = orderMapper.listStatusAuditByOrderNo(orderNo, normalizedSize, offset);
        List<OrderStatusAuditItemResponse> items = records.stream()
                .map(record -> new OrderStatusAuditItemResponse(
                        record.operatorUserId(),
                        record.source(),
                        OrderStatus.fromCode(record.fromStatus()).name(),
                        OrderStatus.fromCode(record.toStatus()).name(),
                        record.reason(),
                        record.createdAt()
                ))
                .toList();
        return new OrderStatusAuditPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public SellerOrderPageResponse listSellerOrders(Long sellerUserId,
                                                    String orderNo,
                                                    String status,
                                                    LocalDateTime createdFrom,
                                                    LocalDateTime createdTo,
                                                    int page,
                                                    int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        Integer statusCode = parseStatusCode(status);
        String normalizedOrderNo = StringUtils.hasText(orderNo) ? orderNo.trim() : null;

        long total = orderMapper.countOrdersBySeller(sellerUserId, normalizedOrderNo, statusCode, createdFrom, createdTo);
        List<OrderRecord> records = orderMapper.listOrdersBySeller(
                sellerUserId,
                normalizedOrderNo,
                statusCode,
                createdFrom,
                createdTo,
                normalizedSize,
                offset
        );
        List<SellerOrderSummaryResponse> items = records.stream()
                .map(record -> new SellerOrderSummaryResponse(
                        record.orderNo(),
                        record.buyerUserId(),
                        OrderStatus.fromCode(record.status()).name(),
                        record.totalAmountCent(),
                        record.itemCount(),
                        record.bizSource(),
                        record.chatConversationId(),
                        record.chatListingId(),
                        record.createdAt(),
                        record.paidAt(),
                        record.updatedAt(),
                        record.refundStatus(),
                        record.refundNo(),
                        record.refundAmountCent(),
                        record.refundUpdatedAt()))
                .toList();
        return new SellerOrderPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public OrderDetailResponse getOrderDetailForSeller(Long sellerUserId, String orderNo) {
        OrderRecord order = orderMapper.findOrderByOrderNo(orderNo);
        if (order == null || isDeleted(order)) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (!order.sellerUserId().equals(sellerUserId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
        List<OrderItemRecord> itemRecords = orderMapper.listOrderItemsByOrderNo(orderNo);
        List<OrderItemResponse> items = itemRecords.stream()
                .map(item -> new OrderItemResponse(
                        item.productId(),
                        item.productNo(),
                        item.skuId(),
                        item.skuNo(),
                        item.skuName(),
                        item.specJson(),
                        item.priceCent(),
                        item.quantity(),
                        item.subtotalCent()))
                .toList();
        OrderReviewAccessState reviewState = orderReviewService.resolveReviewAccessState(order, sellerUserId);
        return new OrderDetailResponse(
                order.orderNo(),
                order.buyerUserId(),
                order.sellerUserId(),
                OrderStatus.fromCode(order.status()).name(),
                order.totalAmountCent(),
                order.bizSource(),
                order.chatConversationId(),
                order.chatListingId(),
                toBool(order.allowMeetup()),
                toBool(order.allowDelivery()),
                order.fulfillmentMode(),
                buildShippingAddress(order),
                order.createdAt(),
                order.paidAt(),
                order.finishedAt(),
                order.timeoutAt(),
                order.refundStatus(),
                order.refundNo(),
                order.refundAmountCent(),
                order.refundUpdatedAt(),
                items,
                reviewState.myReviewSubmitted(),
                reviewState.counterpartyReviewSubmitted(),
                reviewState.canCreateReview(),
                reviewState.canEditReview(),
                reviewState.reviewDeadlineAt()
        );
    }

    public OrderTimelineResponse listOrderTimeline(Long currentUserId, boolean admin, String orderNo, int page, int size) {
        OrderRecord order = orderMapper.findOrderByOrderNo(orderNo);
        if (order == null || isDeleted(order)) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        if (!admin && !order.buyerUserId().equals(currentUserId) && !order.sellerUserId().equals(currentUserId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }

        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        long total = orderMapper.countStatusAuditByOrderNo(orderNo);
        List<OrderStatusAuditRecord> records = orderMapper.listStatusAuditTimelineByOrderNo(orderNo, normalizedSize, offset);
        List<OrderTimelineItemResponse> items = records.stream()
                .map(record -> new OrderTimelineItemResponse(
                        record.source(),
                        record.operatorUserId(),
                        OrderStatus.fromCode(record.fromStatus()).name(),
                        OrderStatus.fromCode(record.toStatus()).name(),
                        record.reason(),
                        record.createdAt()
                )).toList();
        return new OrderTimelineResponse(total, normalizedPage, normalizedSize, items);
    }

    private boolean isDeleted(OrderRecord record) {
        return record.isDeleted() != null && record.isDeleted() == 1;
    }

    private boolean toBool(Integer value) {
        return value != null && value == 1;
    }

    private OrderShippingAddressResponse buildShippingAddress(OrderRecord order) {
        if (order == null || !StringUtils.hasText(order.fulfillmentMode())) {
            return null;
        }
        if (!"DELIVERY".equalsIgnoreCase(order.fulfillmentMode())) {
            return null;
        }
        if (!StringUtils.hasText(order.shippingReceiverName())
                || !StringUtils.hasText(order.shippingReceiverPhone())
                || !StringUtils.hasText(order.shippingProvince())
                || !StringUtils.hasText(order.shippingCity())
                || !StringUtils.hasText(order.shippingDistrict())
                || !StringUtils.hasText(order.shippingDetailAddress())) {
            return null;
        }
        return new OrderShippingAddressResponse(
                order.shippingReceiverName(),
                order.shippingReceiverPhone(),
                order.shippingProvince(),
                order.shippingCity(),
                order.shippingDistrict(),
                order.shippingDetailAddress()
        );
    }

    private void requireOrderExists(String orderNo) {
        OrderRecord order = orderMapper.findOrderByOrderNo(orderNo);
        if (order == null || isDeleted(order)) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
    }

    private Integer parseStatusCode(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase()).getCode();
        } catch (IllegalArgumentException ex) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.BAD_REQUEST);
        }
    }
}
