package moe.hhm.shiori.social.mq;

public record KafkaMessageMetadata(
        String topic,
        int partition,
        long offset,
        String consumerGroup
) {
}
