package moe.hhm.shiori.payment.model;

public record ReconcileTradeLedgerGapRecord(
        String orderNo,
        String paymentNo,
        Integer tradeStatus
) {
}
