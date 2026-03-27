package moe.hhm.shiori.gateway.filter;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class RedisLuaGatewayRateLimiterTest {

    @Test
    void shouldBlockBurstAcrossWindowBoundaryWithSlidingWindow() {
        AtomicLong clock = new AtomicLong();
        AtomicLong nonce = new AtomicLong();
        RedisLuaGatewayRateLimiter limiter = new RedisLuaGatewayRateLimiter(
                new SlidingWindowScriptExecutorFake(),
                new LocalFixedWindowGatewayRateLimiter(clock::get),
                clock::get,
                nonce::incrementAndGet
        );
        GatewayRateLimitRule rule = new GatewayRateLimitRule("login", 5, Duration.ofSeconds(1));

        long[] allowedTimestamps = {900, 920, 940, 960, 980};
        for (long ts : allowedTimestamps) {
            clock.set(ts);
            GatewayRateLimitDecision decision = limiter.acquire(rule, "ip:10.0.0.1").block();
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.degraded()).isFalse();
        }

        clock.set(1_010);
        GatewayRateLimitDecision blocked = limiter.acquire(rule, "ip:10.0.0.1").block();
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterMillis()).isGreaterThan(0);
        assertThat(blocked.degraded()).isFalse();
    }

    @Test
    void shouldFallbackToLocalLimiterWhenRedisScriptFails() {
        AtomicLong clock = new AtomicLong(100);
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
                clock::get,
                new AtomicLong()::incrementAndGet
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

    @Test
    void shouldComposeRedisKeyFromEndpointAndIdentity() {
        AtomicLong clock = new AtomicLong(123);
        CapturingScriptExecutor scriptExecutor = new CapturingScriptExecutor();
        RedisLuaGatewayRateLimiter limiter = new RedisLuaGatewayRateLimiter(
                scriptExecutor,
                new LocalFixedWindowGatewayRateLimiter(clock::get),
                clock::get,
                new AtomicLong(7)::incrementAndGet
        );
        GatewayRateLimitRule rule = new GatewayRateLimitRule("order_create", 3, Duration.ofSeconds(1));

        GatewayRateLimitDecision decision = limiter.acquire(rule, "uid:42").block();

        assertThat(decision.allowed()).isTrue();
        assertThat(scriptExecutor.keys).containsExactly("gateway:rate-limit:order_create:uid:42");
        assertThat(scriptExecutor.args).containsExactly("123", "1000", "3", "123-8");
    }

    private static final class CapturingScriptExecutor implements RedisLuaGatewayRateLimiter.RedisScriptExecutor {
        private List<String> keys;
        private List<String> args;

        @Override
        public Mono<String> execute(RedisScript<String> script, List<String> keys, List<String> args) {
            this.keys = keys;
            this.args = args;
            return Mono.just("1|0");
        }
    }

    private static final class SlidingWindowScriptExecutorFake implements RedisLuaGatewayRateLimiter.RedisScriptExecutor {

        private final Map<String, Deque<Long>> events = new HashMap<>();

        @Override
        public Mono<String> execute(RedisScript<String> script, List<String> keys, List<String> args) {
            String key = keys.getFirst();
            long now = Long.parseLong(args.get(0));
            long window = Long.parseLong(args.get(1));
            int limit = Integer.parseInt(args.get(2));
            Deque<Long> timestamps = events.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= now - window) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= limit) {
                long retryAfter = Math.max(1L, timestamps.peekFirst() + window - now + 1);
                return Mono.just("0|" + retryAfter);
            }
            timestamps.addLast(now);
            return Mono.just("1|0");
        }
    }
}
