package moe.hhm.shiori.order.model;

public record KafkaConsumeLogRecord(
        String consumerGroup,
        String eventId,
        String status
) {
}
