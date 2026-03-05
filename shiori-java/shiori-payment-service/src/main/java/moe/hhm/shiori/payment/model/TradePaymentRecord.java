package moe.hhm.shiori.payment.model;

public record TradePaymentRecord(
        Long id,
        String orderNo,
        String paymentNo,
        Long buyerUserId,
        Long sellerUserId,
        Long amountCent,
        Integer status
) {
}
