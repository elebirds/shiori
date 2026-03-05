package moe.hhm.shiori.payment.dto.internal;

public record ReleaseOrderPaymentResponse(
        String orderNo,
        String paymentNo,
        String status,
        boolean idempotent
) {
}
