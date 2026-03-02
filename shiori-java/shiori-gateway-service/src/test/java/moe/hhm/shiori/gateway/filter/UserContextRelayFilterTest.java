package moe.hhm.shiori.gateway.filter;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextRelayFilterTest {

    private final UserContextRelayFilter filter = new UserContextRelayFilter();

    @Test
    void shouldRelayHeadersAndOverrideForgedValues() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/user/profile")
                .header(UserContextRelayFilter.HEADER_USER_ID, "forged-user")
                .header(UserContextRelayFilter.HEADER_USER_ROLES, "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "HS256")
                .claim("sub", "u1001")
                .claim("roles", List.of("USER", "admin"))
                .build();
        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")), "u1001");

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block();

        ServerHttpRequest forwarded = chain.exchange.getRequest();
        assertThat(forwarded.getHeaders().getFirst(UserContextRelayFilter.HEADER_USER_ID)).isEqualTo("u1001");
        assertThat(forwarded.getHeaders().getFirst(UserContextRelayFilter.HEADER_USER_ROLES))
                .isEqualTo("ROLE_USER,ROLE_ADMIN");
    }

    @Test
    void shouldStripForgedHeadersWhenAnonymous() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/user/profile")
                .header(UserContextRelayFilter.HEADER_USER_ID, "forged-user")
                .header(UserContextRelayFilter.HEADER_USER_ROLES, "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.exchange.getRequest();
        assertThat(forwarded.getHeaders().containsHeader(UserContextRelayFilter.HEADER_USER_ID)).isFalse();
        assertThat(forwarded.getHeaders().containsHeader(UserContextRelayFilter.HEADER_USER_ROLES)).isFalse();
    }

    @Test
    void shouldNotInjectHeadersForNonJwtAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/user/profile").build());
        CapturingChain chain = new CapturingChain();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("u1001", "pwd");

        filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block();

        ServerHttpRequest forwarded = chain.exchange.getRequest();
        assertThat(forwarded.getHeaders().containsHeader(UserContextRelayFilter.HEADER_USER_ID)).isFalse();
        assertThat(forwarded.getHeaders().containsHeader(UserContextRelayFilter.HEADER_USER_ROLES)).isFalse();
    }

    private static class CapturingChain implements GatewayFilterChain {
        private ServerWebExchange exchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.exchange = exchange;
            return Mono.empty();
        }
    }
}
