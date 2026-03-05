package moe.hhm.shiori.gateway.filter;

import java.util.List;
import moe.hhm.shiori.common.security.authz.AuthzHeaderNames;
import moe.hhm.shiori.gateway.security.JwtClaimUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class UserContextRelayFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = stripUserHeaders(exchange);

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .flatMap(authentication -> resolveJwt(authentication)
                        .flatMap(jwt -> chain.filter(addHeaders(sanitizedExchange, jwt))))
                .switchIfEmpty(chain.filter(sanitizedExchange));
    }

    private ServerWebExchange stripUserHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(exchange.getRequest().mutate().headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_ROLES);
                    headers.remove(AuthzHeaderNames.USER_AUTHZ_VERSION);
                    headers.remove(AuthzHeaderNames.USER_AUTHZ_GRANTS);
                    headers.remove(AuthzHeaderNames.USER_AUTHZ_DENIES);
                }).build())
                .build();
    }

    private Mono<Jwt> resolveJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return Mono.just(jwtAuthenticationToken.getToken());
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return Mono.just(jwt);
        }
        return Mono.empty();
    }

    private ServerWebExchange addHeaders(ServerWebExchange exchange, Jwt jwt) {
        String userId = JwtClaimUtils.resolveUserId(jwt);
        List<String> roles = JwtClaimUtils.normalizeRoles(jwt.getClaim("roles"));

        return exchange.mutate()
                .request(exchange.getRequest().mutate().headers(headers -> {
                    if (userId != null) {
                        headers.set(HEADER_USER_ID, userId);
                    }
                    if (!roles.isEmpty()) {
                        headers.set(HEADER_USER_ROLES, String.join(",", roles));
                    }
                }).build())
                .build();
    }
}
