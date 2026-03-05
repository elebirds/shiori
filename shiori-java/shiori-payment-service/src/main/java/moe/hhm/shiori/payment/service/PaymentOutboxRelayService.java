package moe.hhm.shiori.payment.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.payment.config.PaymentMqProperties;
import moe.hhm.shiori.payment.config.PaymentProperties;
import moe.hhm.shiori.payment.model.WalletBalanceOutboxRecord;
import moe.hhm.shiori.payment.repository.PaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "payment.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentOutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxRelayService.class);

    private final PaymentMapper paymentMapper;
    private final RabbitTemplate rabbitTemplate;
    private final PaymentMqProperties paymentMqProperties;
    private final PaymentProperties paymentProperties;

    public PaymentOutboxRelayService(PaymentMapper paymentMapper,
                                     RabbitTemplate rabbitTemplate,
                                     PaymentMqProperties paymentMqProperties,
                                     PaymentProperties paymentProperties) {
        this.paymentMapper = paymentMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.paymentMqProperties = paymentMqProperties;
        this.paymentProperties = paymentProperties;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.relay-fixed-delay-ms:3000}")
    public void relayWalletBalanceEvents() {
        List<WalletBalanceOutboxRecord> candidates = paymentMapper.listWalletBalanceOutboxRelayCandidates(
                paymentProperties.getOutbox().getRelayBatchSize()
        );
        for (WalletBalanceOutboxRecord candidate : candidates) {
            try {
                rabbitTemplate.convertAndSend(
                        paymentMqProperties.getEventExchange(),
                        paymentMqProperties.getWalletBalanceChangedRoutingKey(),
                        candidate.payload()
                );
                paymentMapper.markWalletBalanceOutboxSent(candidate.id());
            } catch (RuntimeException ex) {
                int retryCount = (candidate.retryCount() == null ? 0 : candidate.retryCount()) + 1;
                paymentMapper.markWalletBalanceOutboxFailed(
                        candidate.id(),
                        retryCount,
                        trimError(ex.getMessage()),
                        LocalDateTime.now().plusSeconds(calcBackoffSeconds(retryCount))
                );
                log.warn("钱包余额事件投递失败, eventId={}, retryCount={}, err={}",
                        candidate.eventId(), retryCount, ex.getMessage());
            }
        }
    }

    private int calcBackoffSeconds(int retryCount) {
        int max = Math.max(paymentProperties.getOutbox().getMaxBackoffSeconds(), 1);
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
