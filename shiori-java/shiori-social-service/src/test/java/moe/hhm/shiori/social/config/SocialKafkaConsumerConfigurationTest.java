package moe.hhm.shiori.social.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.FixedBackOff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SocialKafkaConsumerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SocialKafkaConsumerConfiguration.class, KafkaTestBeans.class)
            .withPropertyValues(
                    "social.kafka.enabled=true"
            );

    @Test
    void shouldCreateKafkaErrorHandlerWhenKafkaEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CommonErrorHandler.class);
            assertThat(context).hasBean("kafkaListenerContainerFactory");
            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                    context.getBean("kafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory.class);
            assertThat(factory.getContainerProperties().getAckMode())
                    .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        });
    }

    @Test
    void shouldBuildExponentialBackOffForRetryableFailures() {
        SocialKafkaConsumerConfiguration configuration = new SocialKafkaConsumerConfiguration();

        ExponentialBackOffWithMaxRetries backOff =
                configuration.retryableFailureBackOff(1000L, 3L, 2.0d, 8000L);

        assertThat(backOff.getInitialInterval()).isEqualTo(1000L);
        assertThat(backOff.getMultiplier()).isEqualTo(2.0d);
        assertThat(backOff.getMaxInterval()).isEqualTo(8000L);
        assertThat(backOff.getMaxRetries()).isEqualTo(2);
    }

    @Test
    void shouldKeepFixedBackOffForProcessingInProgress() {
        SocialKafkaConsumerConfiguration configuration = new SocialKafkaConsumerConfiguration();

        FixedBackOff backOff = configuration.processingInProgressBackOff(300000L);

        assertThat(backOff.getInterval()).isEqualTo(300000L);
        assertThat(backOff.getMaxAttempts()).isEqualTo(FixedBackOff.UNLIMITED_ATTEMPTS);
    }

    @Configuration(proxyBeanMethods = false)
    static class KafkaTestBeans {

        @Bean
        KafkaOperations<Object, Object> kafkaOperations() {
            return mock(KafkaOperations.class);
        }

        @Bean
        ConsumerFactory<String, String> consumerFactory() {
            return mock(ConsumerFactory.class);
        }
    }
}
