package moe.hhm.shiori.order.client;

import java.util.List;

public record ProductSkuSnapshot(
        Long skuId,
        String skuNo,
        String displayName,
        List<ProductSpecItemSnapshot> specItems,
        Long priceCent,
        Integer stock,
        String legacySpecJson
) {
    public ProductSkuSnapshot(Long skuId,
                              String skuNo,
                              String displayName,
                              List<ProductSpecItemSnapshot> specItems,
                              Long priceCent,
                              Integer stock) {
        this(skuId, skuNo, displayName, specItems, priceCent, stock, null);
    }

    public ProductSkuSnapshot(Long skuId,
                              String skuNo,
                              String legacySkuName,
                              String legacySpecJson,
                              Long priceCent,
                              Integer stock) {
        this(skuId, skuNo, legacySkuName, null, priceCent, stock, legacySpecJson);
    }
}
