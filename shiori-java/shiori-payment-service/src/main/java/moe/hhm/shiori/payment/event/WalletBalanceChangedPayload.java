package moe.hhm.shiori.payment.event;

public record WalletBalanceChangedPayload(
        Long userId,
        Long availableBalanceCent,
        Long frozenBalanceCent,
        String bizNo,
        String occurredAt
) {
}
