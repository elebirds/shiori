package moe.hhm.shiori.payment.dto.internal;

public record SettleOrderPaymentResponse(
        String orderNo,
        String paymentNo,
        String status,
        boolean idempotent
) {
}
