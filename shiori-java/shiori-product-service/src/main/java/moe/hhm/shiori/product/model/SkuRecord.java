package moe.hhm.shiori.product.model;

public record SkuRecord(
        Long id,
        Long productId,
        String skuNo,
        String skuName,
        String specJson,
        Long priceCent,
        Integer stock,
        Integer isDeleted
) {
}
