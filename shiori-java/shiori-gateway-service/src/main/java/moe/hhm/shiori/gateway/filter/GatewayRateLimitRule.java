package moe.hhm.shiori.gateway.filter;

import java.time.Duration;

record GatewayRateLimitRule(String endpoint, int limit, Duration window) {

    GatewayRateLimitRule {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
    }

    long windowMillis() {
        return window.toMillis();
    }
}
