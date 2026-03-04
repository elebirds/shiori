package moe.hhm.shiori.user.mq;

import moe.hhm.shiori.user.config.UserMqProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnBean(AmqpAdmin.class)
@ConditionalOnProperty(prefix = "user.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UserMqTopology {

    private final AmqpAdmin amqpAdmin;
    private final UserMqProperties properties;

    public UserMqTopology(AmqpAdmin amqpAdmin, UserMqProperties properties) {
        this.amqpAdmin = amqpAdmin;
        this.properties = properties;
    }

    @PostConstruct
    public void declare() {
        TopicExchange eventExchange = new TopicExchange(properties.getEventExchange(), true, false);
        amqpAdmin.declareExchange(eventExchange);
    }
}
