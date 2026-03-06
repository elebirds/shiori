package moe.hhm.shiori.payment.model;

public record TradeRefundRecord(
        Long id,
        String refundNo,
        String orderNo,
        String paymentNo,
        Long buyerUserId,
        Long sellerUserId,
        Long amountCent,
        Integer status,
        String reason
) {
}
