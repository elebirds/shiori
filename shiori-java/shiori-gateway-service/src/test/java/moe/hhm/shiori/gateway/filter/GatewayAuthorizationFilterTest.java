package moe.hhm.shiori.gateway.filter;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.common.security.authz.AuthzHeaderNames;
import moe.hhm.shiori.gateway.config.GatewaySecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayAuthorizationFilterTest {

    @Test
    void shouldAllowAndInjectAuthzHeaders() {
        GatewaySecurityProperties properties = buildProperties();
        GatewayGovernanceMetrics metrics = mock(GatewayGovernanceMetrics.class);
        AuthzSnapshotCacheService cacheService = mock(AuthzSnapshotCacheService.class);
        GatewayAuthorizationFilter filter = new GatewayAuthorizationFilter(properties, metrics, cacheService);

        AuthzSnapshotCacheService.AuthzSnapshotResponse snapshot = new AuthzSnapshotCacheService.AuthzSnapshotResponse(
                1L,
                7L,
                List.of("chat.read"),
                List.of(),
                "2026-03-05T00:00:00Z",
                "2026-03-05T00:00:30Z"
        );
        when(cacheService.resolveSnapshot("1")).thenReturn(
                Mono.just(new AuthzSnapshotCacheService.SnapshotResolveResult(snapshot, "remote", false, false))
        );

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chat/conversations")
                        .header(GatewaySignVerifyFilter.HEADER_USER_ID, "1")
                        .build()
        );
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = gatewayExchange -> {
            captured.set(gatewayExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        verify(metrics).incAuthzDecision("chat.read", "allow");
        ServerWebExchange forwarded = captured.get();
        assertNotNull(forwarded);
        assertEquals("7", forwarded.getRequest().getHeaders().getFirst(AuthzHeaderNames.USER_AUTHZ_VERSION));
        assertEquals("chat.read", forwarded.getRequest().getHeaders().getFirst(AuthzHeaderNames.USER_AUTHZ_GRANTS));
    }

    @Test
    void shouldDenyWhenPermissionInDenies() {
        GatewaySecurityProperties properties = buildProperties();
        GatewayGovernanceMetrics metrics = mock(GatewayGovernanceMetrics.class);
        AuthzSnapshotCacheService cacheService = mock(AuthzSnapshotCacheService.class);
        GatewayAuthorizationFilter filter = new GatewayAuthorizationFilter(properties, metrics, cacheService);

        AuthzSnapshotCacheService.AuthzSnapshotResponse snapshot = new AuthzSnapshotCacheService.AuthzSnapshotResponse(
                1L,
                9L,
                List.of("chat.read"),
                List.of("chat.read"),
                "2026-03-05T00:00:00Z",
                "2026-03-05T00:00:30Z"
        );
        when(cacheService.resolveSnapshot("1")).thenReturn(
                Mono.just(new AuthzSnapshotCacheService.SnapshotResolveResult(snapshot, "remote", false, false))
        );

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chat/conversations")
                        .header(GatewaySignVerifyFilter.HEADER_USER_ID, "1")
                        .build()
        );

        BizException ex = assertThrows(BizException.class, () -> filter.filter(exchange, e -> Mono.empty()).block());
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
        verify(metrics).incAuthzDecision("chat.read", "deny_explicit");
    }

    @Test
    void shouldDegradedAllowAndSetVersionZero() {
        GatewaySecurityProperties properties = buildProperties();
        GatewayGovernanceMetrics metrics = mock(GatewayGovernanceMetrics.class);
        AuthzSnapshotCacheService cacheService = mock(AuthzSnapshotCacheService.class);
        GatewayAuthorizationFilter filter = new GatewayAuthorizationFilter(properties, metrics, cacheService);

        when(cacheService.resolveSnapshot("1")).thenReturn(
                Mono.just(new AuthzSnapshotCacheService.SnapshotResolveResult(null, "no_snapshot", false, true))
        );

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chat/conversations")
                        .header(GatewaySignVerifyFilter.HEADER_USER_ID, "1")
                        .build()
        );
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = gatewayExchange -> {
            captured.set(gatewayExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        verify(metrics).incAuthzDecision("chat.read", "degraded_allow");
        verify(metrics).incAuthzDegradedAllow("no_snapshot");
        ServerWebExchange forwarded = captured.get();
        assertNotNull(forwarded);
        assertEquals("0", forwarded.getRequest().getHeaders().getFirst(AuthzHeaderNames.USER_AUTHZ_VERSION));
    }

    private GatewaySecurityProperties buildProperties() {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        GatewaySecurityProperties.RouteRule rule = new GatewaySecurityProperties.RouteRule();
        rule.setMethod("GET");
        rule.setPathPattern("/api/chat/**");
        rule.setPermissionCode("chat.read");
        properties.getAuthz().setEnabled(true);
        properties.getAuthz().setRouteRules(List.of(rule));
        properties.getAuthz().getDegrade().setAllowWithoutSnapshot(true);
        return properties;
    }
}
