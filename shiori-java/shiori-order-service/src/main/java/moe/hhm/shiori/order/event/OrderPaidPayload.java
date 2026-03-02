package moe.hhm.shiori.order.event;

public record OrderPaidPayload(
        String orderNo,
        String paymentNo,
        Long userId,
        String role,
        Long buyerUserId,
        Long sellerUserId,
        Long totalAmountCent
) {
}
