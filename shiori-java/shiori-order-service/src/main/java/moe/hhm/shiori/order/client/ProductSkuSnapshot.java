package moe.hhm.shiori.order.client;

public record ProductSkuSnapshot(
        Long skuId,
        String skuNo,
        String skuName,
        String specJson,
        Long priceCent,
        Integer stock
) {
}
