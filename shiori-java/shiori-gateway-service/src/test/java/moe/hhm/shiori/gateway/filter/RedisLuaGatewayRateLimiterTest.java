package moe.hhm.shiori.gateway.filter;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class RedisLuaGatewayRateLimiterTest {

    @Test
    void shouldUseGcraLuaScriptAndDerivedArguments() {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(123);
        CapturingScriptExecutor scriptExecutor = new CapturingScriptExecutor();
        RedisLuaGatewayRateLimiter limiter = new RedisLuaGatewayRateLimiter(
                scriptExecutor,
                new LocalFixedWindowGatewayRateLimiter(clock::get),
                clock::get
        );
        GatewayRateLimitRule rule = new GatewayRateLimitRule("order_create", 3, Duration.ofSeconds(1));

        GatewayRateLimitDecision decision = limiter.acquire(rule, "uid:42").block();

        assertThat(decision.allowed()).isTrue();
        assertThat(scriptExecutor.keys).containsExactly("gateway:rate-limit:order_create:uid:42");
        assertThat(scriptExecutor.args).containsExactly("123000", "333334", "666668", "1000");
        assertThat(scriptExecutor.script).contains("redis.call('GET', key)");
        assertThat(scriptExecutor.script).doesNotContain("ZREMRANGEBYSCORE");
    }

    @Test
    void shouldFallbackToLocalLimiterWhenRedisScriptFails() {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(100);
        AtomicInteger fallbackCalls = new AtomicInteger();
        GatewayRateLimiter fallback = (rule, identity) -> {
            int current = fallbackCalls.incrementAndGet();
            if (current == 1) {
                return Mono.just(GatewayRateLimitDecision.allow(false));
            }
            return Mono.just(GatewayRateLimitDecision.block(900, false));
        };
        RedisLuaGatewayRateLimiter limiter = new RedisLuaGatewayRateLimiter(
                (script, keys, args) -> Mono.error(new IllegalStateException("redis down")),
                fallback,
                clock::get
        );
        GatewayRateLimitRule rule = new GatewayRateLimitRule("order_pay", 1, Duration.ofSeconds(1));

        GatewayRateLimitDecision first = limiter.acquire(rule, "uid:1001").block();
        GatewayRateLimitDecision second = limiter.acquire(rule, "uid:1001").block();

        assertThat(first.allowed()).isTrue();
        assertThat(first.degraded()).isTrue();
        assertThat(second.allowed()).isFalse();
        assertThat(second.degraded()).isTrue();
        assertThat(second.retryAfterMillis()).isEqualTo(900);
        assertThat(fallbackCalls.get()).isEqualTo(2);
    }

    private static final class CapturingScriptExecutor implements RedisLuaGatewayRateLimiter.RedisScriptExecutor {
        private List<String> keys;
        private List<String> args;
        private String script;

        @Override
        public Mono<String> execute(RedisScript<String> script, List<String> keys, List<String> args) {
            this.keys = keys;
            this.args = args;
            this.script = script.getScriptAsString();
            return Mono.just("1|0");
        }
    }
}
