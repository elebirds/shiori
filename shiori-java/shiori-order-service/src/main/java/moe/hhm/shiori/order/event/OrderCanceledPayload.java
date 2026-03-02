package moe.hhm.shiori.order.event;

public record OrderCanceledPayload(
        String orderNo,
        Long buyerUserId,
        Long sellerUserId,
        String reason
) {
}
