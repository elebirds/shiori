package moe.hhm.shiori.gateway.filter;

import reactor.core.publisher.Mono;

interface GatewayRateLimiter {

    Mono<GatewayRateLimitDecision> acquire(GatewayRateLimitRule rule, String identity);
}
