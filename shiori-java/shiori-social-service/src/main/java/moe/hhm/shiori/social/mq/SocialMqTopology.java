package moe.hhm.shiori.social.mq;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import moe.hhm.shiori.social.config.SocialMqProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(AmqpAdmin.class)
@ConditionalOnProperty(prefix = "social.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SocialMqTopology {

    private final AmqpAdmin amqpAdmin;
    private final SocialMqProperties properties;

    public SocialMqTopology(AmqpAdmin amqpAdmin, SocialMqProperties properties) {
        this.amqpAdmin = amqpAdmin;
        this.properties = properties;
    }

    @PostConstruct
    public void declare() {
        TopicExchange productEventExchange = new TopicExchange(properties.getProductEventExchange(), true, false);
        amqpAdmin.declareExchange(productEventExchange);

        DirectExchange productPublishedDlxExchange = new DirectExchange(properties.getProductPublishedDlxExchange(), true, false);
        amqpAdmin.declareExchange(productPublishedDlxExchange);

        Map<String, Object> queueArgs = new HashMap<>();
        queueArgs.put("x-dead-letter-exchange", properties.getProductPublishedDlxExchange());
        queueArgs.put("x-dead-letter-routing-key", properties.getProductPublishedDlqRoutingKey());
        Queue productPublishedQueue = new Queue(properties.getProductPublishedQueue(), true, false, false, queueArgs);
        Queue productPublishedDlqQueue = new Queue(properties.getProductPublishedDlqQueue(), true);
        amqpAdmin.declareQueue(productPublishedQueue);
        amqpAdmin.declareQueue(productPublishedDlqQueue);

        Binding productPublishedBinding = BindingBuilder.bind(productPublishedQueue)
                .to(productEventExchange)
                .with(properties.getProductPublishedRoutingKey());
        amqpAdmin.declareBinding(productPublishedBinding);

        Binding productPublishedDlqBinding = BindingBuilder.bind(productPublishedDlqQueue)
                .to(productPublishedDlxExchange)
                .with(properties.getProductPublishedDlqRoutingKey());
        amqpAdmin.declareBinding(productPublishedDlqBinding);
    }
}
