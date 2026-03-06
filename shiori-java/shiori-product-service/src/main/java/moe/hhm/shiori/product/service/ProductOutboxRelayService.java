package moe.hhm.shiori.product.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.product.config.ProductOutboxProperties;
import moe.hhm.shiori.product.model.ProductOutboxEventRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnBean(ProductEventPublisher.class)
@ConditionalOnProperty(prefix = "product.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProductOutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(ProductOutboxRelayService.class);

    private final ProductMapper productMapper;
    private final ProductEventPublisher productEventPublisher;
    private final ProductOutboxProperties productOutboxProperties;

    public ProductOutboxRelayService(ProductMapper productMapper,
                                     ProductEventPublisher productEventPublisher,
                                     ProductOutboxProperties productOutboxProperties) {
        this.productMapper = productMapper;
        this.productEventPublisher = productEventPublisher;
        this.productOutboxProperties = productOutboxProperties;
    }

    @Scheduled(fixedDelayString = "${product.outbox.relay-fixed-delay-ms:3000}")
    public void relayPendingEvents() {
        List<ProductOutboxEventRecord> candidates = productMapper.listProductOutboxRelayCandidates(
                productOutboxProperties.getRelayBatchSize()
        );
        for (ProductOutboxEventRecord event : candidates) {
            try {
                productEventPublisher.publishEnvelope(event.exchangeName(), event.routingKey(), event.payload());
                productMapper.markProductOutboxSent(event.id());
            } catch (RuntimeException ex) {
                int currentRetryCount = event.retryCount() == null ? 0 : event.retryCount();
                int nextRetryCount = currentRetryCount + 1;
                int backoffSeconds = calcBackoffSeconds(nextRetryCount);
                String error = trimError(ex.getMessage());
                productMapper.markProductOutboxFailed(
                        event.id(),
                        nextRetryCount,
                        error,
                        LocalDateTime.now().plusSeconds(backoffSeconds)
                );
                log.warn("product outbox 投递失败, eventId={}, retryCount={}, err={}",
                        event.eventId(), nextRetryCount, error);
            }
        }
    }

    private int calcBackoffSeconds(int retryCount) {
        int max = Math.max(productOutboxProperties.getMaxBackoffSeconds(), 1);
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
