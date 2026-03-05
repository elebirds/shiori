package moe.hhm.shiori.product.model;

public record SkuRecord(
        Long id,
        Long productId,
        String skuNo,
        String displayName,
        String specItemsJson,
        String specSignature,
        String skuName,
        String specJson,
        Long priceCent,
        Integer stock,
        Integer isDeleted
) {
    public SkuRecord(Long id,
                     Long productId,
                     String skuNo,
                     String skuName,
                     String specJson,
                     Long priceCent,
                     Integer stock,
                     Integer isDeleted) {
        this(id, productId, skuNo, null, null, null, skuName, specJson, priceCent, stock, isDeleted);
    }
}
