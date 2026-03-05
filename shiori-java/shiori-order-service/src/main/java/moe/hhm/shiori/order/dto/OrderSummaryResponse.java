package moe.hhm.shiori.order.dto;

import java.time.LocalDateTime;

public record OrderSummaryResponse(
        String orderNo,
        String status,
        Long totalAmountCent,
        Integer itemCount,
        String source,
        Long conversationId,
        Long listingId,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {
}
