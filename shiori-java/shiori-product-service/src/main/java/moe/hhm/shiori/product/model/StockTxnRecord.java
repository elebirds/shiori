package moe.hhm.shiori.product.model;

public record StockTxnRecord(
        Long id,
        String bizNo,
        Long skuId,
        String opType,
        Integer quantity,
        Integer success
) {
}
