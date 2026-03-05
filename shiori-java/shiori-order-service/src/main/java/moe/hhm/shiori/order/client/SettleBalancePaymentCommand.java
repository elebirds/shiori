package moe.hhm.shiori.order.client;

public record SettleBalancePaymentCommand(
        String operatorType,
        Long operatorUserId
) {
}
