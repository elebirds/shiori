package moe.hhm.shiori.gateway.filter;

record GatewayRateLimitDecision(boolean allowed, long retryAfterMillis, boolean degraded) {

    static GatewayRateLimitDecision allow(boolean degraded) {
        return new GatewayRateLimitDecision(true, 0L, degraded);
    }

    static GatewayRateLimitDecision block(long retryAfterMillis, boolean degraded) {
        return new GatewayRateLimitDecision(false, Math.max(1L, retryAfterMillis), degraded);
    }

    GatewayRateLimitDecision withDegraded(boolean degraded) {
        if (this.degraded == degraded) {
            return this;
        }
        return new GatewayRateLimitDecision(allowed, retryAfterMillis, degraded);
    }
}
