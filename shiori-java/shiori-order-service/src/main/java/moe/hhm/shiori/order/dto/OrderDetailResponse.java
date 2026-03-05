package moe.hhm.shiori.order.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        String orderNo,
        Long buyerUserId,
        Long sellerUserId,
        String status,
        Long totalAmountCent,
        String source,
        Long conversationId,
        Long listingId,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime timeoutAt,
        List<OrderItemResponse> items
) {
}
