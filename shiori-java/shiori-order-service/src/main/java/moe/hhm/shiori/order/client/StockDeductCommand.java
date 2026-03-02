package moe.hhm.shiori.order.client;

public record StockDeductCommand(
        Long skuId,
        Integer quantity,
        String bizNo
) {
}
