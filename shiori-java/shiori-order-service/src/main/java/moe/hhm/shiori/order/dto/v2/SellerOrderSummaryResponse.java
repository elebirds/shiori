package moe.hhm.shiori.order.dto.v2;

import java.time.LocalDateTime;

public record SellerOrderSummaryResponse(
        String orderNo,
        Long buyerUserId,
        String status,
        Long totalAmountCent,
        Integer itemCount,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime updatedAt
) {
}
