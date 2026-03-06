package moe.hhm.shiori.payment.mq;

import jakarta.annotation.PostConstruct;
import moe.hhm.shiori.payment.config.PaymentMqProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "payment.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentMqTopology {

    private final AmqpAdmin amqpAdmin;
    private final PaymentMqProperties paymentMqProperties;

    public PaymentMqTopology(AmqpAdmin amqpAdmin, PaymentMqProperties paymentMqProperties) {
        this.amqpAdmin = amqpAdmin;
        this.paymentMqProperties = paymentMqProperties;
    }

    @PostConstruct
    public void declare() {
        TopicExchange eventExchange = new TopicExchange(paymentMqProperties.getEventExchange(), true, false);
        amqpAdmin.declareExchange(eventExchange);
    }
}
