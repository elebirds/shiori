package moe.hhm.shiori.payment.dto;

public record WalletBalanceResponse(
        Long availableBalanceCent,
        Long frozenBalanceCent,
        Long totalBalanceCent
) {
}
