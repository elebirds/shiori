package moe.hhm.shiori.order.dto;

public record OrderOperateResponse(
        String orderNo,
        String status,
        boolean idempotent
) {
}
