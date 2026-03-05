package moe.hhm.shiori.gateway.filter;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayCapabilityBanFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_CAPABILITY_BANS = "X-User-Capability-Bans";

    private final GatewaySecurityProperties properties;
    private final GatewayGovernanceMetrics governanceMetrics;
    private final WebClient webClient;
    private final ConcurrentHashMap<String, CachedCapabilities> cache = new ConcurrentHashMap<>();

    public GatewayCapabilityBanFilter(GatewaySecurityProperties properties,
                                      GatewayGovernanceMetrics governanceMetrics) {
        this.properties = properties;
        this.governanceMetrics = governanceMetrics;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getCapabilityBan().getUserServiceBaseUrl())
                .build();
    }

    @Override
    public int getOrder() {
        return -92;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = stripCapabilityHeader(exchange);
        if (!properties.getCapabilityBan().isEnabled()) {
            return chain.filter(sanitizedExchange);
        }

        String requiredCapability = matchRequiredCapability(sanitizedExchange);
        if (!StringUtils.hasText(requiredCapability)) {
            return chain.filter(sanitizedExchange);
        }

        String userId = sanitizedExchange.getRequest().getHeaders().getFirst(GatewaySignVerifyFilter.HEADER_USER_ID);
        if (!StringUtils.hasText(userId)) {
            return chain.filter(sanitizedExchange);
        }
        return loadActiveCapabilities(userId.trim())
                .flatMap(activeCapabilities -> {
                    if (activeCapabilities.contains(requiredCapability)) {
                        governanceMetrics.incCapabilityCheck(requiredCapability, "blocked");
                        throw new BizException(
                                GatewayErrorCode.ACCESS_DENIED,
                                HttpStatus.FORBIDDEN,
                                "capability=" + requiredCapability
                        );
                    }
                    governanceMetrics.incCapabilityCheck(requiredCapability, "allow");
                    return chain.filter(addCapabilityHeader(sanitizedExchange, activeCapabilities));
                })
                .onErrorResume(throwable -> {
                    if (throwable instanceof BizException) {
                        return Mono.error(throwable);
                    }
                    governanceMetrics.incCapabilityCheck(requiredCapability, "check_error");
                    return chain.filter(sanitizedExchange);
                });
    }

    private String matchRequiredCapability(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getURI().getRawPath();
        if (method == null || !StringUtils.hasText(path)) {
            return null;
        }
        if (path.startsWith("/api/chat/")) {
            if (method == HttpMethod.GET) {
                return "CHAT_READ";
            }
            if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE) {
                return "CHAT_SEND";
            }
            return null;
        }
        if (method == HttpMethod.POST && ("/api/order/orders".equals(path) || "/api/v2/order/orders".equals(path))) {
            return "ORDER_CREATE";
        }
        if (method == HttpMethod.POST && ("/api/product/products".equals(path) || "/api/v2/product/products".equals(path))) {
            return "PRODUCT_PUBLISH";
        }
        if (method == HttpMethod.POST && path.startsWith("/api/product/products/") && path.endsWith("/publish")) {
            return "PRODUCT_PUBLISH";
        }
        if (method == HttpMethod.POST && path.startsWith("/api/v2/product/products/") && path.endsWith("/publish")) {
            return "PRODUCT_PUBLISH";
        }
        return null;
    }

    private Mono<Set<String>> loadActiveCapabilities(String userId) {
        CachedCapabilities cached = cache.get(userId);
        long nowMillis = System.currentTimeMillis();
        if (cached != null && cached.expireAtMillis() > nowMillis) {
            return Mono.just(cached.capabilities());
        }

        int timeoutMs = Math.max(200, properties.getCapabilityBan().getQueryTimeoutMs());
        return webClient.get()
                .uri("/internal/users/{userId}/capabilities/active", userId)
                .retrieve()
                .bodyToMono(ActiveCapabilityListResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(response -> normalizeCapabilities(response.capabilities()))
                .doOnNext(capabilities -> cache.put(
                        userId,
                        new CachedCapabilities(
                                capabilities,
                                nowMillis + Math.max(5, properties.getCapabilityBan().getCacheTtlSeconds()) * 1000L
                        )
                ));
    }

    private ServerWebExchange stripCapabilityHeader(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(exchange.getRequest().mutate().headers(headers -> headers.remove(HEADER_USER_CAPABILITY_BANS)).build())
                .build();
    }

    private ServerWebExchange addCapabilityHeader(ServerWebExchange exchange, Set<String> capabilities) {
        return exchange.mutate()
                .request(exchange.getRequest().mutate().headers(headers -> {
                    headers.remove(HEADER_USER_CAPABILITY_BANS);
                    if (!capabilities.isEmpty()) {
                        headers.set(HEADER_USER_CAPABILITY_BANS, String.join(",", capabilities));
                    }
                }).build())
                .build();
    }

    private Set<String> normalizeCapabilities(java.util.List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return Set.of();
        }
        return capabilities.stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private record CachedCapabilities(Set<String> capabilities, long expireAtMillis) {
    }

    private record ActiveCapabilityListResponse(Long userId, java.util.List<String> capabilities) {
    }
}
