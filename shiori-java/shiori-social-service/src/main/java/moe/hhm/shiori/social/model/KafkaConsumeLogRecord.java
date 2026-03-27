package moe.hhm.shiori.social.model;

import java.time.LocalDateTime;

public record KafkaConsumeLogRecord(
        String consumerGroup,
        String eventId,
        String status,
        LocalDateTime updatedAt
) {
}
