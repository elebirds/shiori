package moe.hhm.shiori.order.client;

public record ReserveBalancePaymentCommand(
        Long buyerUserId,
        Long sellerUserId,
        Long amountCent
) {
}
