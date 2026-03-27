package moe.hhm.shiori.order.mq;

public record KafkaMessageMetadata(
        String topic,
        int partition,
        long offset,
        String consumerGroup
) {
}
