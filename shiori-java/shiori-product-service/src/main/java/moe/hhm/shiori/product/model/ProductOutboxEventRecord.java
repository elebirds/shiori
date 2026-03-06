package moe.hhm.shiori.product.model;

import java.time.LocalDateTime;

public record ProductOutboxEventRecord(
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
