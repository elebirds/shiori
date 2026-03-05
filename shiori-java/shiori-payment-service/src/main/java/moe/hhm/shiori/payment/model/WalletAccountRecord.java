package moe.hhm.shiori.payment.model;

public record WalletAccountRecord(
        Long id,
        Long userId,
        Long availableBalanceCent,
        Long frozenBalanceCent,
        Integer version
) {
}
