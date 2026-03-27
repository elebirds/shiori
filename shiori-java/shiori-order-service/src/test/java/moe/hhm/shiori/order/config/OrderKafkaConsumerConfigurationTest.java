package moe.hhm.shiori.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderKafkaConsumerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OrderKafkaConsumerConfiguration.class, KafkaTestBeans.class)
            .withPropertyValues(
                    "order.kafka.enabled=true"
            );

    @Test
    void shouldCreateKafkaErrorHandlerWhenKafkaEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CommonErrorHandler.class);
            assertThat(context).hasBean("kafkaListenerContainerFactory");
        });
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
