package moe.hhm.shiori.order.model;

public record OrderItemRecord(
        Long id,
        Long orderId,
        String orderNo,
        Long productId,
        String productNo,
        Long skuId,
        String skuNo,
        String skuName,
        String specJson,
        Long priceCent,
        Integer quantity,
        Long subtotalCent,
        Long ownerUserId
) {
}
