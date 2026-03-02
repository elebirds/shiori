package moe.hhm.shiori.gateway.filter;

import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.gateway.config.GatewaySecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRequestSignFilterTest {

    @Test
    void shouldRewriteGatewaySignHeaders() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.getGatewaySign().setInternalSecret("test-internal-sign-secret-32-bytes-0001");
        GatewayRequestSignFilter filter = new GatewayRequestSignFilter(properties);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/order/detail?id=o1")
                .header(GatewaySignVerifyFilter.HEADER_USER_ID, "u1001")
                .header(GatewaySignVerifyFilter.HEADER_USER_ROLES, "ROLE_USER")
                .header(GatewaySignVerifyFilter.HEADER_GATEWAY_TS, "1")
                .header(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN, "forged-sign")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.exchange.getRequest();
        String ts = forwarded.getHeaders().getFirst(GatewaySignVerifyFilter.HEADER_GATEWAY_TS);
        String sign = forwarded.getHeaders().getFirst(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN);

        assertThat(ts).isNotBlank();
        assertThat(sign).isNotBlank().isNotEqualTo("forged-sign");

        String canonical = GatewaySignUtils.buildCanonicalString(
                "GET",
                "/api/order/detail",
                "id=o1",
                "u1001",
                "ROLE_USER",
                ts
        );
        String expected = GatewaySignUtils.hmacSha256Hex(properties.getGatewaySign().getInternalSecret(), canonical);
        assertThat(sign).isEqualTo(expected);
    }

    @Test
    void shouldSkipNonApiPath() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        GatewayRequestSignFilter filter = new GatewayRequestSignFilter(properties);

        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = chain.exchange.getRequest();
        assertThat(forwarded.getHeaders().containsHeader(GatewaySignVerifyFilter.HEADER_GATEWAY_TS)).isFalse();
        assertThat(forwarded.getHeaders().containsHeader(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN)).isFalse();
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
