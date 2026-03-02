package moe.hhm.shiori.product.dto;

public record SkuResponse(
        Long skuId,
        String skuNo,
        String skuName,
        String specJson,
        Long priceCent,
        Integer stock
) {
}
