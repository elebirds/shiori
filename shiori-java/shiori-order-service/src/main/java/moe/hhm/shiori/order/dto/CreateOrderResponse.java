package moe.hhm.shiori.order.dto;

public record CreateOrderResponse(
        String orderNo,
        String status,
        Long totalAmountCent,
        Integer itemCount,
        boolean idempotent
) {
}
