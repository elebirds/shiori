package moe.hhm.shiori.order.model;

import java.math.BigDecimal;

public record OrderReviewRoleAggregateRecord(
        String reviewerRole,
        Long reviewCount,
        BigDecimal avgOverallStar,
        Long positiveCount
) {
}

