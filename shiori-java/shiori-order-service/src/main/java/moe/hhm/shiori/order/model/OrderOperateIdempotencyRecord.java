package moe.hhm.shiori.order.model;

public record OrderOperateIdempotencyRecord(
        Long operatorUserId,
        String operationType,
        String idempotencyKey,
        String orderNo
) {
}
