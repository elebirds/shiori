package moe.hhm.shiori.order.dto.v2;

import java.math.BigDecimal;

public record UserCreditRoleProfileResponse(
        String role,
        long reviewCount,
        BigDecimal avgStar,
        BigDecimal positiveRate
) {
}

