package moe.hhm.shiori.gateway.filter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import moe.hhm.shiori.gateway.config.GatewaySecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuthzSnapshotCacheService {

    private static final Logger log = LoggerFactory.getLogger(AuthzSnapshotCacheService.class);

    private final GatewaySecurityProperties properties;
    private final GatewayGovernanceMetrics governanceMetrics;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    @Nullable
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, CachedSnapshot> l1Cache = new ConcurrentHashMap<>();

    public AuthzSnapshotCacheService(GatewaySecurityProperties properties,
                                     GatewayGovernanceMetrics governanceMetrics,
                                     ObjectMapper objectMapper,
                                     @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder loadBalancedWebClientBuilder,
                                     ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider) {
        this.properties = properties;
        this.governanceMetrics = governanceMetrics;
        this.objectMapper = objectMapper;
        this.webClient = loadBalancedWebClientBuilder
                .baseUrl(properties.getAuthz().getUserServiceBaseUrl())
                .build();
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public Mono<SnapshotResolveResult> resolveSnapshot(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Mono.just(new SnapshotResolveResult(null, "invalid_user", false, true));
        }
        String normalizedUserId = userId.trim();
        long nowMillis = System.currentTimeMillis();
        CachedSnapshot l1 = l1Cache.get(normalizedUserId);
        if (l1 != null && l1.expireAtMillis() > nowMillis) {
            return Mono.just(new SnapshotResolveResult(l1.snapshot(), "l1_fresh", false, false));
        }

        return resolveFromL2(normalizedUserId, nowMillis, true)
                .switchIfEmpty(fetchRemote(normalizedUserId, nowMillis))
                .onErrorResume(error -> resolveStale(normalizedUserId, nowMillis, l1)
                        .switchIfEmpty(allowWithoutSnapshot(error)));
    }

    public void invalidate(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String normalizedUserId = userId.trim();
        l1Cache.remove(normalizedUserId);
        if (!isRedisEnabled()) {
            return;
        }
        redisTemplate.delete(redisKey(normalizedUserId))
                .doOnError(error -> log.warn("invalidate authz redis cache failed: userId={}", normalizedUserId, error))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    private Mono<SnapshotResolveResult> fetchRemote(String userId, long nowMillis) {
        int timeoutMs = Math.max(200, properties.getAuthz().getQueryTimeoutMs());
        int ttlSeconds = Math.max(5, properties.getAuthz().getCache().getTtlSeconds());
        int staleTtlSeconds = Math.max(ttlSeconds, properties.getAuthz().getCache().getStaleTtlSeconds());
        int redisTtlSeconds = Math.max(5, properties.getAuthz().getCache().getRedis().getTtlSeconds());
        int redisStaleTtlSeconds = Math.max(redisTtlSeconds, properties.getAuthz().getCache().getRedis().getStaleTtlSeconds());

        return webClient.get()
                .uri("/internal/authz/users/{userId}/snapshot", userId)
                .retrieve()
                .bodyToMono(AuthzSnapshotResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(this::normalizeSnapshot)
                .map(snapshot -> {
                    CachedSnapshot l1CachedSnapshot = new CachedSnapshot(
                            snapshot,
                            nowMillis + ttlSeconds * 1000L,
                            nowMillis + staleTtlSeconds * 1000L
                    );
                    l1Cache.put(userId, l1CachedSnapshot);
                    writeToL2(
                            userId,
                            new CachedSnapshot(
                                    snapshot,
                                    nowMillis + redisTtlSeconds * 1000L,
                                    nowMillis + redisStaleTtlSeconds * 1000L
                            )
                    );
                    return new SnapshotResolveResult(snapshot, "remote", false, false);
                });
    }

    private Mono<SnapshotResolveResult> resolveStale(String userId, long nowMillis, @Nullable CachedSnapshot l1) {
        if (l1 != null && l1.staleExpireAtMillis() > nowMillis) {
            long staleMillis = Math.max(0L, nowMillis - l1.expireAtMillis());
            governanceMetrics.observeAuthzSnapshotStaleSeconds(staleMillis / 1000D);
            return Mono.just(new SnapshotResolveResult(l1.snapshot(), "l1_stale", true, false));
        }
        return resolveFromL2(userId, nowMillis, false);
    }

    private Mono<SnapshotResolveResult> allowWithoutSnapshot(Throwable error) {
        if (properties.getAuthz().getDegrade().isAllowWithoutSnapshot()) {
            return Mono.just(new SnapshotResolveResult(null, "no_snapshot", false, true));
        }
        return Mono.error(error);
    }

    private Mono<SnapshotResolveResult> resolveFromL2(String userId, long nowMillis, boolean freshOnly) {
        if (!isRedisEnabled()) {
            return Mono.empty();
        }
        return readFromL2(userId)
                .flatMap(cached -> {
                    if (cached.expireAtMillis() > nowMillis) {
                        l1Cache.put(userId, cached);
                        governanceMetrics.incAuthzL2Hit("fresh");
                        return Mono.just(new SnapshotResolveResult(cached.snapshot(), "l2_fresh", false, false));
                    }
                    if (!freshOnly && cached.staleExpireAtMillis() > nowMillis) {
                        l1Cache.put(userId, cached);
                        governanceMetrics.incAuthzL2Hit("stale");
                        long staleMillis = Math.max(0L, nowMillis - cached.expireAtMillis());
                        governanceMetrics.observeAuthzSnapshotStaleSeconds(staleMillis / 1000D);
                        return Mono.just(new SnapshotResolveResult(cached.snapshot(), "l2_stale", true, false));
                    }
                    return Mono.empty();
                });
    }

    private Mono<CachedSnapshot> readFromL2(String userId) {
        return redisTemplate.opsForValue()
                .get(redisKey(userId))
                .flatMap(raw -> {
                    if (!StringUtils.hasText(raw)) {
                        return Mono.empty();
                    }
                    try {
                        RedisCachePayload payload = objectMapper.readValue(raw, RedisCachePayload.class);
                        if (payload == null || payload.snapshot == null) {
                            return Mono.empty();
                        }
                        return Mono.just(new CachedSnapshot(
                                normalizeSnapshot(payload.snapshot),
                                payload.expireAtMillis,
                                payload.staleExpireAtMillis
                        ));
                    } catch (JacksonException ex) {
                        log.warn("parse authz redis cache payload failed: userId={}", userId, ex);
                        return Mono.empty();
                    }
                });
    }

    private void writeToL2(String userId, CachedSnapshot cachedSnapshot) {
        if (!isRedisEnabled()) {
            return;
        }
        String key = redisKey(userId);
        long ttlMillis = Math.max(1L, cachedSnapshot.staleExpireAtMillis() - System.currentTimeMillis());
        RedisCachePayload payload = new RedisCachePayload(
                cachedSnapshot.snapshot(),
                cachedSnapshot.expireAtMillis(),
                cachedSnapshot.staleExpireAtMillis()
        );
        String value;
        try {
            value = objectMapper.writeValueAsString(payload);
        } catch (JacksonException ex) {
            log.warn("serialize authz redis cache payload failed: userId={}", userId, ex);
            return;
        }
        redisTemplate.opsForValue()
                .set(key, value, Duration.ofMillis(ttlMillis))
                .doOnError(error -> log.warn("write authz redis cache failed: userId={}", userId, error))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    private boolean isRedisEnabled() {
        return redisTemplate != null && properties.getAuthz().getCache().getRedis().isEnabled();
    }

    private String redisKey(String userId) {
        String prefix = properties.getAuthz().getCache().getRedis().getKeyPrefix();
        if (!StringUtils.hasText(prefix)) {
            prefix = "authz:snapshot:";
        }
        return prefix + userId;
    }

    private AuthzSnapshotResponse normalizeSnapshot(@Nullable AuthzSnapshotResponse snapshot) {
        if (snapshot == null) {
            return new AuthzSnapshotResponse(null, 0L, List.of(), List.of(), null, null);
        }
        return new AuthzSnapshotResponse(
                snapshot.userId(),
                snapshot.version() == null ? 0L : Math.max(snapshot.version(), 0L),
                new ArrayList<>(normalizeSet(snapshot.grants())),
                new ArrayList<>(normalizeSet(snapshot.denies())),
                snapshot.generatedAt(),
                snapshot.expireAt()
        );
    }

    private Set<String> normalizeSet(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String permission = normalizePermission(value);
            if (StringUtils.hasText(permission)) {
                normalized.add(permission);
            }
        }
        return normalized;
    }

    private String normalizePermission(String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return "";
        }
        return permissionCode.trim().toLowerCase(Locale.ROOT).replace(':', '.');
    }

    public record AuthzSnapshotResponse(Long userId,
                                        Long version,
                                        List<String> grants,
                                        List<String> denies,
                                        String generatedAt,
                                        String expireAt) {
    }

    public record SnapshotResolveResult(AuthzSnapshotResponse snapshot,
                                        String source,
                                        boolean fromStaleCache,
                                        boolean degradedAllow) {
    }

    private record CachedSnapshot(AuthzSnapshotResponse snapshot, long expireAtMillis, long staleExpireAtMillis) {
    }

    private static final class RedisCachePayload {
        public AuthzSnapshotResponse snapshot;
        public long expireAtMillis;
        public long staleExpireAtMillis;

        public RedisCachePayload() {
        }

        private RedisCachePayload(AuthzSnapshotResponse snapshot, long expireAtMillis, long staleExpireAtMillis) {
            this.snapshot = snapshot;
            this.expireAtMillis = expireAtMillis;
            this.staleExpireAtMillis = staleExpireAtMillis;
        }
    }
}
