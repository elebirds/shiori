package moe.hhm.shiori.order.event;

public record WalletBalanceChangedPayload(
        Long userId,
        Long availableBalanceCent,
        Long frozenBalanceCent,
        String bizNo,
        String occurredAt
) {
}
