package moe.hhm.shiori.order.client;

public record RefundBalancePaymentSnapshot(
        String orderNo,
        String refundNo,
        String paymentNo,
        String refundStatus,
        boolean idempotent
) {
}
