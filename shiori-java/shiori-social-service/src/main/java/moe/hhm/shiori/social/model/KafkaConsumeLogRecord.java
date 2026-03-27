package moe.hhm.shiori.social.model;

public record KafkaConsumeLogRecord(
        String consumerGroup,
        String eventId,
        String status
) {
}
