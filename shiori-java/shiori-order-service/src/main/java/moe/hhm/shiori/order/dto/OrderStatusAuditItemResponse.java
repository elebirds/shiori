package moe.hhm.shiori.order.dto;

import java.time.LocalDateTime;

public record OrderStatusAuditItemResponse(
        Long operatorUserId,
        String source,
        String fromStatus,
        String toStatus,
        String reason,
        LocalDateTime createdAt
) {
}
