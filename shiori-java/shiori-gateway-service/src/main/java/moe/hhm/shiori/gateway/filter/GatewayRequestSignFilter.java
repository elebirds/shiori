package moe.hhm.shiori.gateway.filter;

import java.util.UUID;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.gateway.config.GatewaySecurityProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayRequestSignFilter implements GlobalFilter, Ordered {

    private final String internalSecret;

    public GatewayRequestSignFilter(GatewaySecurityProperties properties) {
        String secret = properties.getGatewaySign().getInternalSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("缺少 security.gateway-sign.internal-secret 配置");
        }
        this.internalSecret = secret;
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
        String authzVersion = firstHeader(exchange, GatewaySignVerifyFilter.HEADER_USER_AUTHZ_VERSION);
        String authzGrants = firstHeader(exchange, GatewaySignVerifyFilter.HEADER_USER_AUTHZ_GRANTS);
        String authzDenies = firstHeader(exchange, GatewaySignVerifyFilter.HEADER_USER_AUTHZ_DENIES);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");

        String canonical = GatewaySignUtils.buildCanonicalString(
                exchange.getRequest().getMethod() == null ? "" : exchange.getRequest().getMethod().name(),
                path,
                exchange.getRequest().getURI().getRawQuery(),
                userId,
                userRoles,
                authzVersion,
                authzGrants,
                authzDenies,
                timestamp,
                nonce
        );
        String sign = GatewaySignUtils.hmacSha256Hex(internalSecret, canonical);

        ServerWebExchange signedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate().headers(headers -> {
                    headers.remove(GatewaySignVerifyFilter.HEADER_GATEWAY_TS);
                    headers.remove(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN);
                    headers.remove(GatewaySignVerifyFilter.HEADER_GATEWAY_NONCE);
                    headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_TS, timestamp);
                    headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN, sign);
                    headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_NONCE, nonce);
                }).build())
                .build();

        return chain.filter(signedExchange);
    }

    private String firstHeader(ServerWebExchange exchange, String header) {
        String value = exchange.getRequest().getHeaders().getFirst(header);
        return value == null ? "" : value;
    }
}
