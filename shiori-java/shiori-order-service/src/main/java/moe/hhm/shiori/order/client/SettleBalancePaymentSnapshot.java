package moe.hhm.shiori.order.client;

public record SettleBalancePaymentSnapshot(
        String orderNo,
        String paymentNo,
        String status,
        boolean idempotent
) {
}
