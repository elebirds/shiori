package moe.hhm.shiori.order.service;

import java.util.List;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.domain.OrderStatus;
import moe.hhm.shiori.order.dto.OrderDetailResponse;
import moe.hhm.shiori.order.dto.OrderItemResponse;
import moe.hhm.shiori.order.dto.OrderPageResponse;
import moe.hhm.shiori.order.dto.OrderStatusAuditItemResponse;
import moe.hhm.shiori.order.dto.OrderStatusAuditPageResponse;
import moe.hhm.shiori.order.dto.OrderSummaryResponse;
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

    public OrderService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
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
        return new OrderDetailResponse(
                order.orderNo(),
                order.buyerUserId(),
                order.sellerUserId(),
                OrderStatus.fromCode(order.status()).name(),
                order.totalAmountCent(),
                order.createdAt(),
                order.paidAt(),
                order.timeoutAt(),
                items
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
                        record.createdAt(),
                        record.paidAt()))
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
                        record.createdAt(),
                        record.paidAt()))
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

    private boolean isDeleted(OrderRecord record) {
        return record.isDeleted() != null && record.isDeleted() == 1;
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
