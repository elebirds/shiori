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
    void shouldResolveLoginIdentityFromIp() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.getRateLimit().setEnabled(true);
        CapturingRateLimiter rateLimiter = new CapturingRateLimiter(GatewayRateLimitDecision.allow(false));
        GatewayWriteOpsRateLimitFilter filter = new GatewayWriteOpsRateLimitFilter(
                properties,
                new GatewayGovernanceMetrics(new SimpleMeterRegistry()),
                rateLimiter
        );
        CapturingChain chain = new CapturingChain();

        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/user/auth/login", null, "10.0.0.1");
        filter.filter(exchange, chain).block();

        assertThat(rateLimiter.lastRule).isEqualTo(new GatewayRateLimitRule("login", 20, java.time.Duration.ofSeconds(1)));
        assertThat(rateLimiter.lastIdentity).isEqualTo("ip:10.0.0.1");
        assertThat(chain.called).isTrue();
    }

    @Test
    void shouldBlockOrderPayByUserIdWhenLimiterRejects() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        properties.getRateLimit().setEnabled(true);
        CapturingRateLimiter rateLimiter = new CapturingRateLimiter(GatewayRateLimitDecision.block(750, false));
        GatewayWriteOpsRateLimitFilter filter = new GatewayWriteOpsRateLimitFilter(
                properties,
                new GatewayGovernanceMetrics(new SimpleMeterRegistry()),
                rateLimiter
        );
        CapturingChain chain = new CapturingChain();

        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/v2/order/orders/O001/pay", "1001", null);
        assertThatThrownBy(() -> filter.filter(exchange, chain).block())
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 20004);

        assertThat(rateLimiter.lastRule).isEqualTo(new GatewayRateLimitRule("order_pay", 50, java.time.Duration.ofSeconds(1)));
        assertThat(rateLimiter.lastIdentity).isEqualTo("uid:1001");
        assertThat(chain.called).isFalse();
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

    private static final class CapturingRateLimiter implements GatewayRateLimiter {

        private final GatewayRateLimitDecision decision;
        private GatewayRateLimitRule lastRule;
        private String lastIdentity;

        private CapturingRateLimiter(GatewayRateLimitDecision decision) {
            this.decision = decision;
        }

        @Override
        public Mono<GatewayRateLimitDecision> acquire(GatewayRateLimitRule rule, String identity) {
            lastRule = rule;
            lastIdentity = identity;
            return Mono.just(decision);
        }
    }

    private static final class CapturingChain implements GatewayFilterChain {
        private boolean called;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            called = true;
            return Mono.empty();
        }
    }
}
