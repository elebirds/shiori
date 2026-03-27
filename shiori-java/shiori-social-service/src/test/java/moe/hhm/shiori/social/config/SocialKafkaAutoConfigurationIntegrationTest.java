package moe.hhm.shiori.social.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SocialKafkaAutoConfigurationIntegrationTest {

    private static final String KAFKA_AUTO_CONFIGURATION_CLASS =
            "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration";

    @Test
    void shouldAutoConfigureKafkaBeansNeededByConsumerErrorHandler() {
        ClassLoader classLoader = getClass().getClassLoader();
        assertThat(ClassUtils.isPresent(KAFKA_AUTO_CONFIGURATION_CLASS, classLoader)).isTrue();

        @SuppressWarnings("unchecked")
        Class<?> autoConfigurationClass = ClassUtils.resolveClassName(KAFKA_AUTO_CONFIGURATION_CLASS, classLoader);

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(autoConfigurationClass))
                .withUserConfiguration(SocialKafkaConsumerConfiguration.class)
                .withPropertyValues(
                        "social.kafka.enabled=true",
                        "spring.kafka.bootstrap-servers=localhost:9092",
                        "spring.kafka.consumer.group-id=test-social-group",
                        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KafkaOperations.class);
                    assertThat(context).hasSingleBean(ConsumerFactory.class);
                    assertThat(context).hasSingleBean(CommonErrorHandler.class);
                    assertThat(context).hasBean("kafkaListenerContainerFactory");
                });
    }
}
