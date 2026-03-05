package moe.hhm.shiori.gateway.filter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.gateway.config.GatewaySecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayWriteOpsRateLimitFilterTest {

    @Test
    void shouldRateLimitLoginByIp() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setLoginPerSecond(1);
        GatewayWriteOpsRateLimitFilter filter = new GatewayWriteOpsRateLimitFilter(
                properties,
                new GatewayGovernanceMetrics(new SimpleMeterRegistry())
        );
        CapturingChain chain = new CapturingChain();

        MockServerWebExchange first = exchange(HttpMethod.POST, "/api/user/auth/login", null, "10.0.0.1");
        filter.filter(first, chain).block();

        MockServerWebExchange second = exchange(HttpMethod.POST, "/api/user/auth/login", null, "10.0.0.1");
        assertThatThrownBy(() -> filter.filter(second, chain).block())
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 20004);
    }

    @Test
    void shouldRateLimitOrderPayByUserId() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setOrderPayPerSecond(1);
        GatewayWriteOpsRateLimitFilter filter = new GatewayWriteOpsRateLimitFilter(
                properties,
                new GatewayGovernanceMetrics(new SimpleMeterRegistry())
        );
        CapturingChain chain = new CapturingChain();

        MockServerWebExchange first = exchange(HttpMethod.POST, "/api/v2/order/orders/O001/pay", "1001", null);
        filter.filter(first, chain).block();

        MockServerWebExchange second = exchange(HttpMethod.POST, "/api/v2/order/orders/O001/pay", "1001", null);
        assertThatThrownBy(() -> filter.filter(second, chain).block())
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 20004);

        MockServerWebExchange third = exchange(HttpMethod.POST, "/api/v2/order/orders/O001/pay", "1002", null);
        filter.filter(third, chain).block();
        assertThat(chain.called).isTrue();
    }

    private MockServerWebExchange exchange(HttpMethod method, String path, String userId, String xForwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path);
        if (userId != null) {
            builder.header(GatewaySignVerifyFilter.HEADER_USER_ID, userId);
        }
        if (xForwardedFor != null) {
            builder.header("X-Forwarded-For", xForwardedFor);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private static class CapturingChain implements GatewayFilterChain {
        private boolean called;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            called = true;
            return Mono.empty();
        }
    }
}
