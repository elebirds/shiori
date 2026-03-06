package moe.hhm.shiori.order.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderReviewRecord(
        Long id,
        String orderNo,
        Long reviewerUserId,
        Long reviewedUserId,
        String reviewerRole,
        Integer communicationStar,
        Integer timelinessStar,
        Integer credibilityStar,
        BigDecimal overallStar,
        String comment,
        String imageObjectKeys,
        String visibilityStatus,
        String visibilityReason,
        Long visibilityOperatorUserId,
        LocalDateTime visibilityUpdatedAt,
        Integer editCount,
        LocalDateTime lastEditedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
