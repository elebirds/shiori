package moe.hhm.shiori.product.mq;

import jakarta.annotation.PostConstruct;
import moe.hhm.shiori.product.config.ProductMqProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(AmqpAdmin.class)
@ConditionalOnProperty(prefix = "product.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProductMqTopology {

    private final AmqpAdmin amqpAdmin;
    private final ProductMqProperties properties;

    public ProductMqTopology(AmqpAdmin amqpAdmin, ProductMqProperties properties) {
        this.amqpAdmin = amqpAdmin;
        this.properties = properties;
    }

    @PostConstruct
    public void declare() {
        TopicExchange eventExchange = new TopicExchange(properties.getEventExchange(), true, false);
        amqpAdmin.declareExchange(eventExchange);
    }
}
