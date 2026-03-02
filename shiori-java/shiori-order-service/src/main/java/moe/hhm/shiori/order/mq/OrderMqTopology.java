package moe.hhm.shiori.order.mq;

import java.util.HashMap;
import java.util.Map;
import moe.hhm.shiori.order.config.OrderMqProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnBean(AmqpAdmin.class)
@ConditionalOnProperty(prefix = "order.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderMqTopology {

    private final AmqpAdmin amqpAdmin;
    private final OrderMqProperties properties;

    public OrderMqTopology(AmqpAdmin amqpAdmin, OrderMqProperties properties) {
        this.amqpAdmin = amqpAdmin;
        this.properties = properties;
    }

    @PostConstruct
    public void declare() {
        TopicExchange eventExchange = new TopicExchange(properties.getEventExchange(), true, false);
        amqpAdmin.declareExchange(eventExchange);

        DirectExchange delayExchange = new DirectExchange(properties.getDelayExchange(), true, false);
        DirectExchange timeoutDlxExchange = new DirectExchange(properties.getTimeoutDlxExchange(), true, false);
        amqpAdmin.declareExchange(delayExchange);
        amqpAdmin.declareExchange(timeoutDlxExchange);

        Map<String, Object> delayQueueArgs = new HashMap<>();
        delayQueueArgs.put("x-message-ttl", properties.getTimeoutTtlMs());
        delayQueueArgs.put("x-dead-letter-exchange", properties.getTimeoutDlxExchange());
        delayQueueArgs.put("x-dead-letter-routing-key", properties.getDelayRoutingKey());

        Queue timeoutDelayQueue = new Queue(properties.getTimeoutDelayQueue(), true, false, false, delayQueueArgs);
        Queue timeoutConsumeQueue = new Queue(properties.getTimeoutConsumeQueue(), true);
        amqpAdmin.declareQueue(timeoutDelayQueue);
        amqpAdmin.declareQueue(timeoutConsumeQueue);

        Binding delayBinding = BindingBuilder.bind(timeoutDelayQueue)
                .to(delayExchange)
                .with(properties.getDelayRoutingKey());
        Binding consumeBinding = BindingBuilder.bind(timeoutConsumeQueue)
                .to(timeoutDlxExchange)
                .with(properties.getDelayRoutingKey());
        amqpAdmin.declareBinding(delayBinding);
        amqpAdmin.declareBinding(consumeBinding);
    }
}
