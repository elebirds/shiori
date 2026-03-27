package moe.hhm.shiori.social.config;

import moe.hhm.shiori.social.mq.NonRetryableKafkaConsumerException;
import moe.hhm.shiori.social.mq.KafkaProcessingInProgressException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "social.kafka", name = "enabled", havingValue = "true")
public class SocialKafkaConsumerConfiguration {

    @Bean
    CommonErrorHandler socialKafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations,
                                               @Value("${social.kafka.retry-initial-interval-ms:1000}") long retryInitialIntervalMs,
                                               @Value("${social.kafka.max-attempts:3}") long maxAttempts,
                                               @Value("${social.kafka.retry-multiplier:2.0}") double retryMultiplier,
                                               @Value("${social.kafka.retry-max-interval-ms:8000}") long retryMaxIntervalMs,
                                               @Value("${social.kafka.processing-timeout-ms:300000}") long processingTimeoutMs,
                                               @Value("${social.kafka.dlt-suffix:.dlt}") String dltSuffix) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (ConsumerRecord<?, ?> record, Exception ex) -> new TopicPartition(record.topic() + dltSuffix, record.partition())
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                retryableFailureBackOff(retryInitialIntervalMs, maxAttempts, retryMultiplier, retryMaxIntervalMs)
        );
        errorHandler.addNotRetryableExceptions(NonRetryableKafkaConsumerException.class);
        FixedBackOff processingInProgressBackOff = processingInProgressBackOff(processingTimeoutMs);
        errorHandler.setBackOffFunction((record, ex) ->
                ex instanceof KafkaProcessingInProgressException ? processingInProgressBackOff : null);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    ExponentialBackOffWithMaxRetries retryableFailureBackOff(long retryInitialIntervalMs,
                                                             long maxAttempts,
                                                             double retryMultiplier,
                                                             long retryMaxIntervalMs) {
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries((int) Math.max(maxAttempts - 1L, 0L));
        backOff.setInitialInterval(Math.max(retryInitialIntervalMs, 1L));
        backOff.setMultiplier(Math.max(retryMultiplier, 1.0d));
        backOff.setMaxInterval(Math.max(retryMaxIntervalMs, Math.max(retryInitialIntervalMs, 1L)));
        return backOff;
    }

    FixedBackOff processingInProgressBackOff(long processingTimeoutMs) {
        return new FixedBackOff(Math.max(processingTimeoutMs, 1L), FixedBackOff.UNLIMITED_ATTEMPTS);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler socialKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(socialKafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
