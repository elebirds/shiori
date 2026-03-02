package moe.hhm.shiori.order.event;

public record OrderTimeoutPayload(
        String orderNo,
        Long buyerUserId
) {
}
