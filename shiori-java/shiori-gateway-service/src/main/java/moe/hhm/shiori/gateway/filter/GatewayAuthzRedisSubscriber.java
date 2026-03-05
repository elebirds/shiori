package moe.hhm.shiori.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import moe.hhm.shiori.gateway.config.GatewaySecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class GatewayAuthzRedisSubscriber {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthzRedisSubscriber.class);

    private final GatewaySecurityProperties properties;
    private final GatewayGovernanceMetrics governanceMetrics;
    private final AuthzSnapshotCacheService cacheService;
    private final ObjectMapper objectMapper;
    @Nullable
    private final RedisConnectionFactory redisConnectionFactory;
    @Nullable
    private RedisMessageListenerContainer listenerContainer;

    public GatewayAuthzRedisSubscriber(GatewaySecurityProperties properties,
                                       GatewayGovernanceMetrics governanceMetrics,
                                       AuthzSnapshotCacheService cacheService,
                                       ObjectMapper objectMapper,
                                       ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider) {
        this.properties = properties;
        this.governanceMetrics = governanceMetrics;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.redisConnectionFactory = redisConnectionFactoryProvider.getIfAvailable();
    }

    @PostConstruct
    public void start() {
        if (!properties.getAuthz().getEvent().isEnabled()) {
            return;
        }
        if (redisConnectionFactory == null) {
            log.warn("authz redis subscriber disabled: missing redis connection factory");
            return;
        }
        String channel = properties.getAuthz().getEvent().getRedisChannel();
        if (!StringUtils.hasText(channel)) {
            channel = "shiori.authz.user.changed";
        }
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(new AuthzMessageListener(), new ChannelTopic(channel.trim()));
        try {
            container.afterPropertiesSet();
        } catch (Exception ex) {
            log.warn("authz redis subscriber init failed", ex);
            return;
        }
        container.start();
        listenerContainer = container;
    }

    @PreDestroy
    public void stop() {
        if (listenerContainer != null) {
            listenerContainer.stop();
            try {
                listenerContainer.destroy();
            } catch (Exception ex) {
                log.warn("authz redis subscriber destroy failed", ex);
            }
            listenerContainer = null;
        }
    }

    private void handleMessage(String raw) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        try {
            AuthzUserChangedEvent event = objectMapper.readValue(raw, AuthzUserChangedEvent.class);
            if (event == null || event.userId == null || event.userId <= 0) {
                return;
            }
            cacheService.invalidate(String.valueOf(event.userId));
            if (StringUtils.hasText(event.changedAt)) {
                try {
                    long lagMs = Math.max(0L, Instant.now().toEpochMilli() - Instant.parse(event.changedAt).toEpochMilli());
                    governanceMetrics.observeAuthzEventLagMs((double) lagMs);
                } catch (RuntimeException ex) {
                    log.debug("parse authz event changedAt failed: {}", event.changedAt);
                }
            }
        } catch (JacksonException ex) {
            log.warn("parse authz redis event failed: payload={}", raw, ex);
        }
    }

    private final class AuthzMessageListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            if (message == null || message.getBody() == null || message.getBody().length == 0) {
                return;
            }
            handleMessage(new String(message.getBody(), StandardCharsets.UTF_8));
        }
    }

    private static final class AuthzUserChangedEvent {
        public Long userId;
        public Long version;
        public String changedAt;
        public String reason;
    }
}
