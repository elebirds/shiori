package moe.hhm.shiori.order.client;

public record RefundBalancePaymentCommand(
        String refundNo,
        String operatorType,
        Long operatorUserId,
        String reason
) {
}
