package moe.hhm.shiori.order.dto.v2;

import java.math.BigDecimal;

public record UserCreditCompositeResponse(
        long totalReviewCount,
        BigDecimal compositeAvgStar,
        Integer compositeScore100,
        String creditGrade
) {
}

