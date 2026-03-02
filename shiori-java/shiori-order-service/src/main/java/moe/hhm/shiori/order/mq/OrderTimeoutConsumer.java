package moe.hhm.shiori.order.mq;

import moe.hhm.shiori.order.event.EventEnvelope;
import moe.hhm.shiori.order.event.OrderTimeoutPayload;
import moe.hhm.shiori.order.service.OrderCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnBean(name = "rabbitListenerContainerFactory")
@ConditionalOnProperty(prefix = "order.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderCommandService orderCommandService;

    public OrderTimeoutConsumer(ObjectMapper objectMapper, OrderCommandService orderCommandService) {
        this.objectMapper = objectMapper;
        this.orderCommandService = orderCommandService;
    }

    @RabbitListener(queues = "${order.mq.timeout-consume-queue:q.order.timeout.consume}")
    public void onTimeoutMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return;
        }

        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message, EventEnvelope.class);
        } catch (JacksonException ex) {
            log.warn("忽略非法超时消息: {}", ex.getMessage());
            return;
        }
        if (envelope == null || !"OrderTimeout".equals(envelope.type())) {
            return;
        }

        String orderNo = envelope.aggregateId();
        try {
            if (envelope.payload() != null && !envelope.payload().isNull()) {
                OrderTimeoutPayload payload = objectMapper.treeToValue(envelope.payload(), OrderTimeoutPayload.class);
                if (payload != null && StringUtils.hasText(payload.orderNo())) {
                    orderNo = payload.orderNo();
                }
            }
        } catch (JacksonException ex) {
            log.warn("忽略无效 OrderTimeout payload: {}", ex.getMessage());
            return;
        }

        if (!StringUtils.hasText(orderNo)) {
            log.warn("忽略缺失 orderNo 的 OrderTimeout 事件: {}", envelope.eventId());
            return;
        }
        orderCommandService.handleTimeout(orderNo);
    }
}
