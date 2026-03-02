package moe.hhm.shiori.order.dto;

public record OrderItemResponse(
        Long productId,
        String productNo,
        Long skuId,
        String skuNo,
        String skuName,
        String specJson,
        Long priceCent,
        Integer quantity,
        Long subtotalCent
) {
}
