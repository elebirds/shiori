package moe.hhm.shiori.gateway.filter;

import java.util.List;
import java.util.Objects;
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
    private static final RedisScript<String> GCRA_SCRIPT = RedisScript.of("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local emission = tonumber(ARGV[2])
            local burst_tolerance = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])

            local existing = redis.call('GET', key)
            local tat
            if existing then
                tat = tonumber(existing)
            else
                tat = now
            end

            local allow_at = tat - burst_tolerance
            if now < allow_at then
                if existing then
                    redis.call('PEXPIRE', key, ttl)
                end
                local retry_after = math.max(1, math.ceil((allow_at - now) / 1000))
                return '0|' .. tostring(retry_after)
            end

            local new_tat = math.max(now, tat) + emission
            redis.call('SET', key, tostring(new_tat), 'PX', ttl)
            return '1|0'
            """, String.class);

    @Nullable
    private final RedisScriptExecutor redisScriptExecutor;
    private final GatewayRateLimiter fallbackLimiter;
    private final LongSupplier timeSupplier;

    @Autowired
    public RedisLuaGatewayRateLimiter(ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider) {
        this(
                createExecutor(redisTemplateProvider.getIfAvailable()),
                new LocalFixedWindowGatewayRateLimiter(System::currentTimeMillis),
                System::currentTimeMillis
        );
    }

    RedisLuaGatewayRateLimiter(@Nullable RedisScriptExecutor redisScriptExecutor,
                               GatewayRateLimiter fallbackLimiter,
                               LongSupplier timeSupplier) {
        this.redisScriptExecutor = redisScriptExecutor;
        this.fallbackLimiter = Objects.requireNonNull(fallbackLimiter);
        this.timeSupplier = Objects.requireNonNull(timeSupplier);
    }

    @Override
    public Mono<GatewayRateLimitDecision> acquire(GatewayRateLimitRule rule, String identity) {
        if (redisScriptExecutor == null) {
            return fallbackLimiter.acquire(rule, identity).map(decision -> decision.withDegraded(true));
        }

        long nowMillis = timeSupplier.getAsLong();
        GcraRateLimitState gcraState = GcraRateLimitState.initial(rule);
        String key = KEY_PREFIX + rule.endpoint() + ":" + identity;
        List<String> keys = List.of(key);
        List<String> args = List.of(
                Long.toString(nowMillis * 1000L),
                Long.toString(gcraState.emissionIntervalMicros()),
                Long.toString(gcraState.burstToleranceMicros()),
                Long.toString(gcraState.ttlMillis())
        );

        return redisScriptExecutor.execute(GCRA_SCRIPT, keys, args)
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
