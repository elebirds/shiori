package moe.hhm.shiori.order.model;

import java.time.LocalDateTime;

public record OrderStatusAuditRecord(
        Long id,
        String orderNo,
        Long operatorUserId,
        String source,
        Integer fromStatus,
        Integer toStatus,
        String reason,
        LocalDateTime createdAt
) {
}
