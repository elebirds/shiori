package moe.hhm.shiori.payment.dto.internal;

public record RefundOrderPaymentResponse(
        String orderNo,
        String refundNo,
        String paymentNo,
        String refundStatus,
        boolean idempotent
) {
}
