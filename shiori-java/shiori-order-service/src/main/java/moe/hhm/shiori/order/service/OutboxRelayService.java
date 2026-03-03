package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.model.OutboxEventRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "order.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;
    private final OrderProperties orderProperties;
    private final OrderMetrics orderMetrics;

    public OutboxRelayService(OrderMapper orderMapper,
                              RabbitTemplate rabbitTemplate,
                              OrderProperties orderProperties,
                              OrderMetrics orderMetrics) {
        this.orderMapper = orderMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.orderProperties = orderProperties;
        this.orderMetrics = orderMetrics;
    }

    @Scheduled(fixedDelayString = "${order.outbox.relay-fixed-delay-ms:3000}")
    public void relayPendingEvents() {
        List<OutboxEventRecord> candidates = orderMapper.listRelayCandidates(orderProperties.getOutbox().getRelayBatchSize());
        for (OutboxEventRecord event : candidates) {
            try {
                rabbitTemplate.convertAndSend(event.exchangeName(), event.routingKey(), event.payload());
                orderMapper.markOutboxSent(event.id());
                orderMetrics.incOutboxRelay("sent", event.type());
            } catch (RuntimeException ex) {
                int currentRetryCount = event.retryCount() == null ? 0 : event.retryCount();
                int nextRetryCount = currentRetryCount + 1;
                int backoffSeconds = calcBackoffSeconds(nextRetryCount);
                String error = trimError(ex.getMessage());
                orderMapper.markOutboxFailed(event.id(), nextRetryCount, error,
                        LocalDateTime.now().plusSeconds(backoffSeconds));
                orderMetrics.incOutboxRelay("failed", event.type());
                log.warn("outbox 投递失败, eventId={}, retryCount={}, err={}",
                        event.eventId(), nextRetryCount, error);
            }
        }
    }

    private int calcBackoffSeconds(int retryCount) {
        int max = Math.max(orderProperties.getOutbox().getMaxBackoffSeconds(), 1);
        int exponent = Math.min(Math.max(retryCount - 1, 0), 12);
        long value = 1L << exponent;
        return (int) Math.min(value, max);
    }

    private String trimError(String message) {
        if (!StringUtils.hasText(message)) {
            return "unknown";
        }
        String normalized = message.trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500);
    }
}
