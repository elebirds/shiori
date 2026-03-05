package moe.hhm.shiori.order.model;

public record CartRecord(
        Long id,
        Long buyerUserId,
        Long sellerUserId
) {
}
