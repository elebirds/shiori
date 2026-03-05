package moe.hhm.shiori.order.dto.v2;

public record UserCreditProfileResponse(
        Long userId,
        UserCreditRoleProfileResponse buyerProfile,
        UserCreditRoleProfileResponse sellerProfile,
        UserCreditCompositeResponse composite
) {
}

