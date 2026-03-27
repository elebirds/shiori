package moe.hhm.shiori.order.config;

import moe.hhm.shiori.order.mq.NonRetryableKafkaConsumerException;
import moe.hhm.shiori.order.mq.KafkaProcessingInProgressException;
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
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "order.kafka", name = "enabled", havingValue = "true")
public class OrderKafkaConsumerConfiguration {

    @Bean
    CommonErrorHandler orderKafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations,
                                              @Value("${order.kafka.retry-initial-interval-ms:1000}") long retryInitialIntervalMs,
                                              @Value("${order.kafka.max-attempts:3}") long maxAttempts,
                                              @Value("${order.kafka.retry-multiplier:2.0}") double retryMultiplier,
                                              @Value("${order.kafka.retry-max-interval-ms:8000}") long retryMaxIntervalMs,
                                              @Value("${order.kafka.processing-timeout-ms:300000}") long processingTimeoutMs,
                                              @Value("${order.kafka.dlt-suffix:.dlt}") String dltSuffix) {
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
            CommonErrorHandler orderKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(orderKafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
