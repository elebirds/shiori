package moe.hhm.shiori.gateway.filter;

import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.gateway.config.GatewaySecurityProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayRequestSignFilter implements GlobalFilter, Ordered {

    private final GatewaySecurityProperties properties;

    public GatewayRequestSignFilter(GatewaySecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return -90;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path == null || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        String userId = firstHeader(exchange, GatewaySignVerifyFilter.HEADER_USER_ID);
        String userRoles = firstHeader(exchange, GatewaySignVerifyFilter.HEADER_USER_ROLES);
        String timestamp = String.valueOf(System.currentTimeMillis());

        String canonical = GatewaySignUtils.buildCanonicalString(
                exchange.getRequest().getMethod() == null ? "" : exchange.getRequest().getMethod().name(),
                path,
                exchange.getRequest().getURI().getRawQuery(),
                userId,
                userRoles,
                timestamp
        );
        String sign = GatewaySignUtils.hmacSha256Hex(properties.getGatewaySign().getInternalSecret(), canonical);

        ServerWebExchange signedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate().headers(headers -> {
                    headers.remove(GatewaySignVerifyFilter.HEADER_GATEWAY_TS);
                    headers.remove(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN);
                    headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_TS, timestamp);
                    headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN, sign);
                }).build())
                .build();

        return chain.filter(signedExchange);
    }

    private String firstHeader(ServerWebExchange exchange, String header) {
        String value = exchange.getRequest().getHeaders().getFirst(header);
        return value == null ? "" : value;
    }
}
