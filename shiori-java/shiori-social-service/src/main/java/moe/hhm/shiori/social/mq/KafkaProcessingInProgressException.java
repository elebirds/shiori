package moe.hhm.shiori.social.mq;

public class KafkaProcessingInProgressException extends RuntimeException {

    public KafkaProcessingInProgressException(String eventId) {
        super("kafka event is still being processed: " + eventId);
    }
}
