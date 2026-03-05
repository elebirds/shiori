package moe.hhm.shiori.user.authz.service;

import java.time.Instant;
import moe.hhm.shiori.user.config.UserAuthzProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

@Service
public class AuthzEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuthzEventPublisher.class);

    private final UserAuthzProperties properties;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AuthzEventPublisher(UserAuthzProperties properties,
                               @Nullable StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishAfterCommit(Long userId, long version, String reason) {
        if (userId == null || userId <= 0 || version <= 0) {
            return;
        }
        if (!properties.getEvent().isEnabled()) {
            return;
        }
        if (redisTemplate == null) {
            return;
        }
        String channel = StringUtils.hasText(properties.getEvent().getRedisChannel())
                ? properties.getEvent().getRedisChannel().trim()
                : "shiori.authz.user.changed";
        if (!StringUtils.hasText(channel)) {
            return;
        }

        AuthzUserChangedEvent payload = new AuthzUserChangedEvent(
                userId,
                version,
                Instant.now().toString(),
                StringUtils.hasText(reason) ? reason.trim() : null
        );
        Runnable publishAction = () -> publishNow(channel, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishAction.run();
                }
            });
            return;
        }
        publishAction.run();
    }

    private void publishNow(String channel, AuthzUserChangedEvent payload) {
        try {
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(payload));
        } catch (JacksonException ex) {
            log.warn("serialize authz event failed: userId={}", payload.userId(), ex);
        } catch (RuntimeException ex) {
            log.warn("publish authz event failed: channel={}, userId={}", channel, payload.userId(), ex);
        }
    }

    private record AuthzUserChangedEvent(Long userId, Long version, String changedAt, String reason) {
    }
}
