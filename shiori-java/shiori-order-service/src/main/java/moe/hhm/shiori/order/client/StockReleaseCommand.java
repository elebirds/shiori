package moe.hhm.shiori.order.client;

public record StockReleaseCommand(
        Long skuId,
        Integer quantity,
        String bizNo
) {
}
