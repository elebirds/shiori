package moe.hhm.shiori.order.dto.v2;

import java.time.LocalDateTime;

public record OrderTimelineItemResponse(
        String source,
        Long operatorUserId,
        String fromStatus,
        String toStatus,
        String reason,
        LocalDateTime createdAt
) {
}
