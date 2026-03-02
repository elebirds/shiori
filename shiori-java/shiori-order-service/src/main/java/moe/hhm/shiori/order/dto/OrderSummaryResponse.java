package moe.hhm.shiori.order.dto;

import java.time.LocalDateTime;

public record OrderSummaryResponse(
        String orderNo,
        String status,
        Long totalAmountCent,
        Integer itemCount,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {
}
