package moe.hhm.shiori.user.outbox.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.user.admin.repository.AdminUserMapper;
import moe.hhm.shiori.user.config.UserOutboxProperties;
import moe.hhm.shiori.user.outbox.model.UserOutboxEventRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnBean(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "user.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UserOutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(UserOutboxRelayService.class);

    private final AdminUserMapper adminUserMapper;
    private final RabbitTemplate rabbitTemplate;
    private final UserOutboxProperties userOutboxProperties;

    public UserOutboxRelayService(AdminUserMapper adminUserMapper,
                                  RabbitTemplate rabbitTemplate,
                                  UserOutboxProperties userOutboxProperties) {
        this.adminUserMapper = adminUserMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.userOutboxProperties = userOutboxProperties;
    }

    @Scheduled(fixedDelayString = "${user.outbox.relay-fixed-delay-ms:3000}")
    public void relayPendingEvents() {
        List<UserOutboxEventRecord> candidates = adminUserMapper.listOutboxRelayCandidates(userOutboxProperties.getRelayBatchSize());
        for (UserOutboxEventRecord event : candidates) {
            try {
                rabbitTemplate.convertAndSend(event.exchangeName(), event.routingKey(), event.payload());
                adminUserMapper.markOutboxSent(event.id());
            } catch (RuntimeException ex) {
                int currentRetryCount = event.retryCount() == null ? 0 : event.retryCount();
                int nextRetryCount = currentRetryCount + 1;
                int backoffSeconds = calcBackoffSeconds(nextRetryCount);
                String error = trimError(ex.getMessage());
                adminUserMapper.markOutboxFailed(
                        event.id(),
                        nextRetryCount,
                        error,
                        LocalDateTime.now().plusSeconds(backoffSeconds)
                );
                log.warn("user outbox 投递失败, eventId={}, retryCount={}, err={}", event.eventId(), nextRetryCount, error);
            }
        }
    }

    private int calcBackoffSeconds(int retryCount) {
        int max = Math.max(userOutboxProperties.getMaxBackoffSeconds(), 1);
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
