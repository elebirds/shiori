package moe.hhm.shiori.order.client;

public record ReserveBalancePaymentSnapshot(
        String orderNo,
        String paymentNo,
        String status,
        boolean idempotent
) {
}
