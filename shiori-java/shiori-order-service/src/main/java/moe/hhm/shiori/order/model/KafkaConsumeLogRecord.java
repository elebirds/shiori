package moe.hhm.shiori.order.model;

import java.time.LocalDateTime;

public record KafkaConsumeLogRecord(
        String consumerGroup,
        String eventId,
        String status,
        LocalDateTime updatedAt
) {
}
