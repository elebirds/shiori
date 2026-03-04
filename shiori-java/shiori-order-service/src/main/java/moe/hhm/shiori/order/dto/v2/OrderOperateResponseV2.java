package moe.hhm.shiori.order.dto.v2;

public record OrderOperateResponseV2(
        String orderNo,
        String status,
        boolean idempotent
) {
}

