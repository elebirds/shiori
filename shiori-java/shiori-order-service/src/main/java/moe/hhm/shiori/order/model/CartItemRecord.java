package moe.hhm.shiori.order.model;

public record CartItemRecord(
        Long id,
        Long cartId,
        Long buyerUserId,
        Long sellerUserId,
        Long productId,
        Long skuId,
        Integer quantity
) {
}
