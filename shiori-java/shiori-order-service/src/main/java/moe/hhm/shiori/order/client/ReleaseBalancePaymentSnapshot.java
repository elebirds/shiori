package moe.hhm.shiori.order.client;

public record ReleaseBalancePaymentSnapshot(
        String orderNo,
        String paymentNo,
        String status,
        boolean idempotent
) {
}
