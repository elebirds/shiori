package moe.hhm.shiori.order.dto.v2;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderReviewItemResponse(
        Long reviewId,
        String orderNo,
        Long reviewerUserId,
        Long reviewedUserId,
        String reviewerRole,
        Integer communicationStar,
        Integer timelinessStar,
        Integer credibilityStar,
        BigDecimal overallStar,
        String comment,
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

