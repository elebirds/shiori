package moe.hhm.shiori.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import moe.hhm.shiori.common.error.GatewayErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.common.security.authz.AuthzHeaderNames;
import moe.hhm.shiori.gateway.config.GatewaySecurityProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthorizationFilter implements GlobalFilter, Ordered {

    private final GatewaySecurityProperties properties;
    private final GatewayGovernanceMetrics governanceMetrics;
    private final AuthzSnapshotCacheService authzSnapshotCacheService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayAuthorizationFilter(GatewaySecurityProperties properties,
                                      GatewayGovernanceMetrics governanceMetrics,
                                      AuthzSnapshotCacheService authzSnapshotCacheService) {
        this.properties = properties;
        this.governanceMetrics = governanceMetrics;
        this.authzSnapshotCacheService = authzSnapshotCacheService;
    }

    @Override
    public int getOrder() {
        return -92;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitizedExchange = stripAuthzHeaders(exchange);
        if (!properties.getAuthz().isEnabled()) {
            return chain.filter(sanitizedExchange);
        }

        MatchedRule matchedRule = matchRouteRule(sanitizedExchange);
        if (matchedRule == null) {
            return chain.filter(sanitizedExchange);
        }

        String userId = resolveUserId(sanitizedExchange);
        if (!StringUtils.hasText(userId)) {
            return chain.filter(sanitizedExchange);
        }

        String normalizedUserId = userId.trim();
        String normalizedPermission = normalizePermission(matchedRule.permissionCode());

        return authzSnapshotCacheService.resolveSnapshot(normalizedUserId)
                .flatMap(resolveResult -> {
                    if (resolveResult.degradedAllow()) {
                        governanceMetrics.incAuthzDecision(normalizedPermission, "degraded_allow");
                        governanceMetrics.incAuthzDegradedAllow(resolveResult.source());
                        return chain.filter(addAuthzHeaders(sanitizedExchange, null));
                    }

                    Decision decision = evaluate(normalizedPermission, resolveResult.snapshot());
                    if (!decision.allowed()) {
                        governanceMetrics.incAuthzDecision(normalizedPermission, decision.reason());
                        throw new BizException(
                                GatewayErrorCode.ACCESS_DENIED,
                                HttpStatus.FORBIDDEN,
                                "permission=" + normalizedPermission
                        );
                    }

                    String decisionReason = resolveResult.fromStaleCache() ? "allow_stale" : "allow";
                    governanceMetrics.incAuthzDecision(normalizedPermission, decisionReason);
                    return chain.filter(addAuthzHeaders(sanitizedExchange, resolveResult.snapshot()));
                })
                .onErrorResume(throwable -> {
                    if (throwable instanceof BizException) {
                        return Mono.error(throwable);
                    }
                    if (properties.getAuthz().getDegrade().isAllowWithoutSnapshot()) {
                        governanceMetrics.incAuthzDecision(normalizedPermission, "degraded_allow");
                        governanceMetrics.incAuthzDegradedAllow("snapshot_error");
                        return chain.filter(addAuthzHeaders(sanitizedExchange, null));
                    }
                    return Mono.error(throwable);
                });
    }

    private Decision evaluate(String permissionCode, AuthzSnapshotCacheService.AuthzSnapshotResponse snapshot) {
        if (snapshot == null) {
            return new Decision(false, "deny_no_snapshot");
        }
        Set<String> denies = normalizeSet(snapshot.denies());
        if (denies.contains(permissionCode)) {
            return new Decision(false, "deny_explicit");
        }
        Set<String> grants = normalizeSet(snapshot.grants());
        if (grants.contains(permissionCode)) {
            return new Decision(true, "allow");
        }
        return new Decision(false, "deny_default");
    }

    private MatchedRule matchRouteRule(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getURI().getRawPath();
        if (method == null || !StringUtils.hasText(path)) {
            return null;
        }
        List<GatewaySecurityProperties.RouteRule> rules = properties.getAuthz().getRouteRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (GatewaySecurityProperties.RouteRule rule : rules) {
            if (rule == null || !StringUtils.hasText(rule.getPathPattern()) || !StringUtils.hasText(rule.getPermissionCode())) {
                continue;
            }
            if (!pathMatcher.match(rule.getPathPattern().trim(), path)) {
                continue;
            }
            String methodRule = StringUtils.hasText(rule.getMethod()) ? rule.getMethod().trim().toUpperCase(Locale.ROOT) : "*";
            if (!"*".equals(methodRule) && !method.name().equalsIgnoreCase(methodRule)) {
                continue;
            }
            return new MatchedRule(rule.getPathPattern().trim(), methodRule, rule.getPermissionCode().trim());
        }
        return null;
    }

    private String resolveUserId(ServerWebExchange exchange) {
        String headerUserId = exchange.getRequest().getHeaders().getFirst(GatewaySignVerifyFilter.HEADER_USER_ID);
        if (StringUtils.hasText(headerUserId)) {
            return headerUserId.trim();
        }
        String accessToken = exchange.getRequest().getQueryParams().getFirst("accessToken");
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }
        return parseUserIdFromAccessToken(accessToken.trim());
    }

    private String parseUserIdFromAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(properties.getJwt().getHmacSecret())) {
            return null;
        }
        try {
            SignedJWT signedJWT = SignedJWT.parse(accessToken);
            if (!signedJWT.verify(new MACVerifier(properties.getJwt().getHmacSecret().getBytes(StandardCharsets.UTF_8)))) {
                return null;
            }
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            Date expiresAt = claimsSet.getExpirationTime();
            if (expiresAt != null && expiresAt.before(new Date())) {
                return null;
            }
            Object uid = claimsSet.getClaim("uid");
            if (uid == null) {
                uid = claimsSet.getSubject();
            }
            if (uid == null) {
                return null;
            }
            String value = String.valueOf(uid).trim();
            return value.isEmpty() ? null : value;
        } catch (java.text.ParseException | JOSEException e) {
            return null;
        }
    }

    private Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String permission = normalizePermission(value);
            if (StringUtils.hasText(permission)) {
                normalized.add(permission);
            }
        }
        return normalized;
    }

    private String normalizePermission(String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return "";
        }
        return permissionCode.trim().toLowerCase(Locale.ROOT).replace(':', '.');
    }

    private ServerWebExchange stripAuthzHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(exchange.getRequest().mutate().headers(headers -> {
                    headers.remove(AuthzHeaderNames.USER_AUTHZ_VERSION);
                    headers.remove(AuthzHeaderNames.USER_AUTHZ_GRANTS);
                    headers.remove(AuthzHeaderNames.USER_AUTHZ_DENIES);
                }).build())
                .build();
    }

    private ServerWebExchange addAuthzHeaders(ServerWebExchange exchange,
                                              AuthzSnapshotCacheService.AuthzSnapshotResponse snapshot) {
        return exchange.mutate()
                .request(exchange.getRequest().mutate().headers(headers -> {
                    headers.remove(AuthzHeaderNames.USER_AUTHZ_VERSION);
                    headers.remove(AuthzHeaderNames.USER_AUTHZ_GRANTS);
                    headers.remove(AuthzHeaderNames.USER_AUTHZ_DENIES);
                    if (snapshot == null) {
                        headers.set(AuthzHeaderNames.USER_AUTHZ_VERSION, "0");
                        return;
                    }
                    headers.set(AuthzHeaderNames.USER_AUTHZ_VERSION, String.valueOf(snapshot.version() == null ? 0L : snapshot.version()));
                    if (snapshot.grants() != null && !snapshot.grants().isEmpty()) {
                        headers.set(AuthzHeaderNames.USER_AUTHZ_GRANTS, String.join(",", normalizeSet(snapshot.grants())));
                    }
                    if (snapshot.denies() != null && !snapshot.denies().isEmpty()) {
                        headers.set(AuthzHeaderNames.USER_AUTHZ_DENIES, String.join(",", normalizeSet(snapshot.denies())));
                    }
                }).build())
                .build();
    }

    private record MatchedRule(String pathPattern, String method, String permissionCode) {
    }

    private record Decision(boolean allowed, String reason) {
    }
}
