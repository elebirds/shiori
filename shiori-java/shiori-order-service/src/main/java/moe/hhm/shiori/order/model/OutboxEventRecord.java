package moe.hhm.shiori.order.model;

import java.time.LocalDateTime;

public record OutboxEventRecord(
        Long id,
        String eventId,
        String aggregateId,
        String type,
        String payload,
        String exchangeName,
        String routingKey,
        String status,
        Integer retryCount,
        String lastError,
        LocalDateTime nextRetryAt,
        LocalDateTime createdAt,
        LocalDateTime sentAt
) {
}
