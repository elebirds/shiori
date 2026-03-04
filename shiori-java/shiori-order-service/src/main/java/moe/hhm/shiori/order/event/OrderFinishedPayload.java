package moe.hhm.shiori.order.event;

import java.time.Instant;

public record OrderFinishedPayload(
        String orderNo,
        Long buyerUserId,
        Long sellerUserId,
        String operatorType,
        Long operatorUserId,
        Instant occurredAt
) {
}
