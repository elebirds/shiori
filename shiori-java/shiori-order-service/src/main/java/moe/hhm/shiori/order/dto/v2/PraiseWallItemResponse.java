package moe.hhm.shiori.order.dto.v2;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PraiseWallItemResponse(
        Long reviewId,
        String orderNo,
        Long reviewerUserId,
        String reviewerRole,
        Integer communicationStar,
        Integer timelinessStar,
        Integer credibilityStar,
        BigDecimal overallStar,
        String comment,
        LocalDateTime createdAt
) {
}

