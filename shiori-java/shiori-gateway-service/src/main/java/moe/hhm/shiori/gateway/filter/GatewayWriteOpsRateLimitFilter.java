package moe.hhm.shiori.gateway.filter;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong cleanupTick = new AtomicLong();

    public GatewayWriteOpsRateLimitFilter(GatewaySecurityProperties properties,
                                          GatewayGovernanceMetrics governanceMetrics) {
        this.properties = properties;
        this.governanceMetrics = governanceMetrics;
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

        EndpointRule rule = matchRule(exchange);
        if (rule == null) {
            return chain.filter(exchange);
        }

        long nowSecond = System.currentTimeMillis() / 1000;
        String identity = resolveIdentity(exchange, rule.endpoint());
        String counterKey = rule.endpoint() + ":" + identity;

        WindowCounter updated = counters.compute(counterKey, (key, current) -> {
            if (current == null || current.epochSecond() != nowSecond) {
                return new WindowCounter(nowSecond, 1);
            }
            return new WindowCounter(nowSecond, current.count() + 1);
        });

        if (updated != null && updated.count() > rule.limitPerSecond()) {
            governanceMetrics.incRateLimit(rule.endpoint(), "blocked");
            throw new BizException(GatewayErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "endpoint=" + rule.endpoint());
        }

        governanceMetrics.incRateLimit(rule.endpoint(), "allow");
        if ((cleanupTick.incrementAndGet() & 0xFF) == 0) {
            cleanup(nowSecond);
        }
        return chain.filter(exchange);
    }

    private EndpointRule matchRule(ServerWebExchange exchange) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST) {
            return null;
        }

        String path = exchange.getRequest().getURI().getRawPath();
        if (!StringUtils.hasText(path)) {
            return null;
        }
        if ("/api/user/auth/login".equals(path)) {
            return new EndpointRule("login", Math.max(1, properties.getRateLimit().getLoginPerSecond()));
        }
        if ("/api/order/orders".equals(path)) {
            return new EndpointRule("order_create", Math.max(1, properties.getRateLimit().getOrderCreatePerSecond()));
        }
        if (path.startsWith("/api/order/orders/") && path.endsWith("/pay")) {
            return new EndpointRule("order_pay", Math.max(1, properties.getRateLimit().getOrderPayPerSecond()));
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

    private void cleanup(long nowSecond) {
        counters.entrySet().removeIf(entry -> entry.getValue().epochSecond() < nowSecond);
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

    private record EndpointRule(String endpoint, int limitPerSecond) {
    }

    private record WindowCounter(long epochSecond, int count) {
    }
}
