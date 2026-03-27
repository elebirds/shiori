package moe.hhm.shiori.gateway.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import reactor.core.publisher.Mono;

final class LocalFixedWindowGatewayRateLimiter implements GatewayRateLimiter {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong cleanupTick = new AtomicLong();
    private final LongSupplier timeSupplier;

    LocalFixedWindowGatewayRateLimiter(LongSupplier timeSupplier) {
        this.timeSupplier = timeSupplier;
    }

    @Override
    public Mono<GatewayRateLimitDecision> acquire(GatewayRateLimitRule rule, String identity) {
        long nowMillis = timeSupplier.getAsLong();
        long windowMillis = Math.max(1L, rule.windowMillis());
        long bucket = Math.floorDiv(nowMillis, windowMillis);
        String counterKey = rule.endpoint() + ":" + identity;

        WindowCounter updated = counters.compute(counterKey, (key, current) -> {
            if (current == null || current.bucket() != bucket) {
                return new WindowCounter(bucket, 1);
            }
            return new WindowCounter(bucket, current.count() + 1);
        });

        if ((cleanupTick.incrementAndGet() & 0xFF) == 0) {
            cleanup(bucket);
        }

        if (updated != null && updated.count() > rule.limit()) {
            long retryAfterMillis = ((bucket + 1) * windowMillis) - nowMillis;
            return Mono.just(GatewayRateLimitDecision.block(retryAfterMillis, false));
        }
        return Mono.just(GatewayRateLimitDecision.allow(false));
    }

    private void cleanup(long activeBucket) {
        counters.entrySet().removeIf(entry -> entry.getValue().bucket() < activeBucket);
        int maxEntries = 20000;
        if (counters.size() <= maxEntries) {
            return;
        }
        int trimmed = 0;
        int trimTarget = counters.size() - maxEntries;
        for (Map.Entry<String, WindowCounter> entry : counters.entrySet()) {
            if (trimmed >= trimTarget) {
                break;
            }
            if (counters.remove(entry.getKey(), entry.getValue())) {
                trimmed++;
            }
        }
    }

    private record WindowCounter(long bucket, int count) {
    }
}
