package moe.hhm.shiori.product.dto;

public record StockOperateResponse(
        boolean success,
        boolean idempotent,
        String bizNo,
        Long skuId,
        Integer quantity,
        Integer currentStock
) {
}
