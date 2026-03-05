package moe.hhm.shiori.payment.dto;

public record RedeemCdkResponse(
        Long redeemAmountCent,
        Long availableBalanceCent,
        Long frozenBalanceCent
) {
}
