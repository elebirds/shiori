package moe.hhm.shiori.social.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "social.kafka", name = "enabled", havingValue = "true")
public class SocialKafkaConsumerConfiguration {

    @Bean
    CommonErrorHandler socialKafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(new FixedBackOff(0L, 0L));
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }
}
