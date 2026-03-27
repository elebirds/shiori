package moe.hhm.shiori.social.mq;

public class NonRetryableKafkaConsumerException extends RuntimeException {

    public NonRetryableKafkaConsumerException(String message) {
        super(message);
    }

    public NonRetryableKafkaConsumerException(String message, Throwable cause) {
        super(message, cause);
    }
}
