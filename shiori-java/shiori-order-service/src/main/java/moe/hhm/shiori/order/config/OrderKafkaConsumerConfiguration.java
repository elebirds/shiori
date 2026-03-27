package moe.hhm.shiori.order.config;

import moe.hhm.shiori.order.mq.NonRetryableKafkaConsumerException;
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
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "order.kafka", name = "enabled", havingValue = "true")
public class OrderKafkaConsumerConfiguration {

    @Bean
    CommonErrorHandler orderKafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations,
                                              @Value("${order.kafka.retry-interval-ms:1000}") long retryIntervalMs,
                                              @Value("${order.kafka.max-attempts:3}") long maxAttempts,
                                              @Value("${order.kafka.dlt-suffix:.dlt}") String dltSuffix) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (ConsumerRecord<?, ?> record, Exception ex) -> new TopicPartition(record.topic() + dltSuffix, record.partition())
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(Math.max(retryIntervalMs, 0L), Math.max(maxAttempts - 1L, 0L))
        );
        errorHandler.addNotRetryableExceptions(NonRetryableKafkaConsumerException.class);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
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
