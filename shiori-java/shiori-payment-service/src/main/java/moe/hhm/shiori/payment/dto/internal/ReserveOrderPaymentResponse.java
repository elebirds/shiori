package moe.hhm.shiori.payment.dto.internal;

public record ReserveOrderPaymentResponse(
        String orderNo,
        String paymentNo,
        String status,
        boolean idempotent
) {
}
