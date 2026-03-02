package moe.hhm.shiori.order.client;

public record StockOperateSnapshot(
        boolean success,
        boolean idempotent,
        String bizNo,
        Long skuId,
        Integer quantity,
        Integer currentStock
) {
}
