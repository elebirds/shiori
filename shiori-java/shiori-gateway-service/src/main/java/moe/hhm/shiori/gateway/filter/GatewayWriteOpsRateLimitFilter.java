package moe.hhm.shiori.gateway.filter;

import java.net.InetSocketAddress;
import java.time.Duration;
import moe.hhm.shiori.common.error.GatewayErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.gateway.config.GatewaySecurityProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayWriteOpsRateLimitFilter implements GlobalFilter, Ordered {

    private final GatewaySecurityProperties properties;
    private final GatewayGovernanceMetrics governanceMetrics;
    private final GatewayRateLimiter rateLimiter;

    public GatewayWriteOpsRateLimitFilter(GatewaySecurityProperties properties,
                                          GatewayGovernanceMetrics governanceMetrics,
                                          GatewayRateLimiter rateLimiter) {
        this.properties = properties;
        this.governanceMetrics = governanceMetrics;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public int getOrder() {
        return -95;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.getRateLimit().isEnabled()) {
            return chain.filter(exchange);
        }

        GatewayRateLimitRule rule = matchRule(exchange);
        if (rule == null) {
            return chain.filter(exchange);
        }

        String identity = resolveIdentity(exchange, rule.endpoint());
        return rateLimiter.acquire(rule, identity)
                .flatMap(decision -> {
                    governanceMetrics.incRateLimit(rule.endpoint(), metricDecision(decision));
                    if (!decision.allowed()) {
                        return Mono.error(new BizException(
                                GatewayErrorCode.RATE_LIMITED,
                                HttpStatus.TOO_MANY_REQUESTS,
                                "endpoint=" + rule.endpoint() + ";retryAfterMs=" + decision.retryAfterMillis()
                        ));
                    }
                    return chain.filter(exchange);
                });
    }

    private GatewayRateLimitRule matchRule(ServerWebExchange exchange) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST) {
            return null;
        }

        String path = exchange.getRequest().getURI().getRawPath();
        if (!StringUtils.hasText(path)) {
            return null;
        }
        if ("/api/user/auth/login".equals(path)) {
            return new GatewayRateLimitRule("login", Math.max(1, properties.getRateLimit().getLoginPerSecond()), Duration.ofSeconds(1));
        }
        if ("/api/v2/order/orders".equals(path)) {
            return new GatewayRateLimitRule("order_create", Math.max(1, properties.getRateLimit().getOrderCreatePerSecond()), Duration.ofSeconds(1));
        }
        if (path.startsWith("/api/v2/order/orders/") && path.endsWith("/pay")) {
            return new GatewayRateLimitRule("order_pay", Math.max(1, properties.getRateLimit().getOrderPayPerSecond()), Duration.ofSeconds(1));
        }
        return null;
    }

    private String resolveIdentity(ServerWebExchange exchange, String endpoint) {
        if (!"login".equals(endpoint)) {
            String userId = exchange.getRequest().getHeaders().getFirst(GatewaySignVerifyFilter.HEADER_USER_ID);
            if (StringUtils.hasText(userId)) {
                return "uid:" + userId.trim();
            }
        }

        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String clientIp = forwardedFor.split(",")[0].trim();
            if (StringUtils.hasText(clientIp)) {
                return "ip:" + clientIp;
            }
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return "ip:" + remoteAddress.getAddress().getHostAddress();
        }
        return "ip:unknown";
    }

    private String metricDecision(GatewayRateLimitDecision decision) {
        if (decision.degraded()) {
            return decision.allowed() ? "degraded_allow" : "degraded_block";
        }
        return decision.allowed() ? "allow" : "blocked";
    }
}
