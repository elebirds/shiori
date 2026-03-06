package moe.hhm.shiori.order.dto.v2;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        List<String> imageObjectKeys,
        LocalDateTime createdAt
) {
}
