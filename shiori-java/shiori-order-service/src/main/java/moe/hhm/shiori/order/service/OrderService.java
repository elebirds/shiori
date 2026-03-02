package moe.hhm.shiori.order.service;

import java.util.List;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.domain.OrderStatus;
import moe.hhm.shiori.order.dto.OrderDetailResponse;
import moe.hhm.shiori.order.dto.OrderItemResponse;
import moe.hhm.shiori.order.dto.OrderPageResponse;
import moe.hhm.shiori.order.dto.OrderSummaryResponse;
import moe.hhm.shiori.order.model.OrderItemRecord;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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

    private boolean isDeleted(OrderRecord record) {
        return record.isDeleted() != null && record.isDeleted() == 1;
    }
}
