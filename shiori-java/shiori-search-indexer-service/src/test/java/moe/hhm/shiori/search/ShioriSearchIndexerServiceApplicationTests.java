package moe.hhm.shiori.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ShioriSearchIndexerServiceApplication.class)
class ShioriSearchIndexerServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldAutoConfigureKafkaListenerInfrastructure() {
        assertThat(applicationContext.containsBean("kafkaListenerContainerFactory")).isTrue();
    }

    @Test
    void shouldUseManualAckModeForKafkaListener() {
        ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                applicationContext.getBean("kafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory.class);

        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL);
    }
}
