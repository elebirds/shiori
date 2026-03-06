package moe.hhm.shiori.social.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SocialRabbitAdminConfiguration {

    @Bean
    @ConditionalOnMissingBean(AmqpAdmin.class)
    @ConditionalOnProperty(prefix = "social.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AmqpAdmin socialAmqpAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
