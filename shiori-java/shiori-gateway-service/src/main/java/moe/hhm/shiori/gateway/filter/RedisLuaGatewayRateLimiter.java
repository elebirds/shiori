package moe.hhm.shiori.gateway.filter;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisLuaGatewayRateLimiter implements GatewayRateLimiter {

    private static final String KEY_PREFIX = "gateway:rate-limit:";
    private static final RedisScript<String> SLIDING_WINDOW_SCRIPT = RedisScript.of("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            local window_start = now - window

            redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)
            local count = redis.call('ZCARD', key)
            if count >= limit then
                local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                local retry_after = window
                if oldest and #oldest >= 2 then
                    local oldest_score = tonumber(oldest[2])
                    retry_after = math.max(1, oldest_score + window - now + 1)
                end
                redis.call('PEXPIRE', key, window)
                return '0|' .. tostring(retry_after)
            end

            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return '1|0'
            """, String.class);

    @Nullable
    private final RedisScriptExecutor redisScriptExecutor;
    private final GatewayRateLimiter fallbackLimiter;
    private final LongSupplier timeSupplier;
    private final LongSupplier nonceSupplier;

    @Autowired
    public RedisLuaGatewayRateLimiter(ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider) {
        this(
                createExecutor(redisTemplateProvider.getIfAvailable()),
                new LocalFixedWindowGatewayRateLimiter(System::currentTimeMillis),
                System::currentTimeMillis,
                new AtomicLong()::incrementAndGet
        );
    }

    RedisLuaGatewayRateLimiter(@Nullable RedisScriptExecutor redisScriptExecutor,
                               GatewayRateLimiter fallbackLimiter,
                               LongSupplier timeSupplier,
                               LongSupplier nonceSupplier) {
        this.redisScriptExecutor = redisScriptExecutor;
        this.fallbackLimiter = Objects.requireNonNull(fallbackLimiter);
        this.timeSupplier = Objects.requireNonNull(timeSupplier);
        this.nonceSupplier = Objects.requireNonNull(nonceSupplier);
    }

    @Override
    public Mono<GatewayRateLimitDecision> acquire(GatewayRateLimitRule rule, String identity) {
        if (redisScriptExecutor == null) {
            return fallbackLimiter.acquire(rule, identity).map(decision -> decision.withDegraded(true));
        }

        long nowMillis = timeSupplier.getAsLong();
        String key = KEY_PREFIX + rule.endpoint() + ":" + identity;
        List<String> keys = List.of(key);
        List<String> args = List.of(
                Long.toString(nowMillis),
                Long.toString(rule.windowMillis()),
                Integer.toString(rule.limit()),
                nowMillis + "-" + nonceSupplier.getAsLong()
        );

        return redisScriptExecutor.execute(SLIDING_WINDOW_SCRIPT, keys, args)
                .map(this::parseDecision)
                .onErrorResume(error -> fallbackLimiter.acquire(rule, identity)
                        .map(decision -> decision.withDegraded(true)));
    }

    private GatewayRateLimitDecision parseDecision(String raw) {
        String[] parts = raw == null ? new String[0] : raw.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("unexpected rate-limit script response: " + raw);
        }
        if ("1".equals(parts[0])) {
            return GatewayRateLimitDecision.allow(false);
        }
        if ("0".equals(parts[0])) {
            return GatewayRateLimitDecision.block(Long.parseLong(parts[1]), false);
        }
        throw new IllegalStateException("unexpected rate-limit decision flag: " + raw);
    }

    @Nullable
    private static RedisScriptExecutor createExecutor(@Nullable ReactiveStringRedisTemplate redisTemplate) {
        if (redisTemplate == null) {
            return null;
        }
        return new ReactiveRedisScriptExecutor(redisTemplate);
    }

    interface RedisScriptExecutor {
        Mono<String> execute(RedisScript<String> script, List<String> keys, List<String> args);
    }

    private static final class ReactiveRedisScriptExecutor implements RedisScriptExecutor {

        private final ReactiveStringRedisTemplate redisTemplate;

        private ReactiveRedisScriptExecutor(ReactiveStringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        @Override
        public Mono<String> execute(RedisScript<String> script, List<String> keys, List<String> args) {
            return redisTemplate.execute(script, keys, args)
                    .next()
                    .switchIfEmpty(Mono.error(new IllegalStateException("rate-limit script returned empty result")));
        }
    }
}
