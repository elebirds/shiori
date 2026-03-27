package moe.hhm.shiori.search.mq;

public record KafkaMessageMetadata(
        String topic,
        int partition,
        long offset,
        String consumerGroup
) {
}
