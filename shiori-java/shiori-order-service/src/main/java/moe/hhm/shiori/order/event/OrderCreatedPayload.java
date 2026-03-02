package moe.hhm.shiori.order.event;

public record OrderCreatedPayload(
        String orderNo,
        Long buyerUserId,
        Long sellerUserId,
        Long totalAmountCent,
        Integer itemCount
) {
}
