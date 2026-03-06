package moe.hhm.shiori.payment.model;

public record ReconcileRefundLedgerGapRecord(
        String refundNo,
        String orderNo,
        String paymentNo
) {
}
