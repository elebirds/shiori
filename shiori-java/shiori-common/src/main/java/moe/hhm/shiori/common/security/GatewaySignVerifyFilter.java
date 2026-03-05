package moe.hhm.shiori.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import io.micrometer.core.instrument.MeterRegistry;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.CommonErrorCode;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class GatewaySignVerifyFilter extends OncePerRequestFilter {

    public static final String HEADER_GATEWAY_TS = "X-Gateway-Ts";
    public static final String HEADER_GATEWAY_SIGN = "X-Gateway-Sign";
    public static final String HEADER_GATEWAY_NONCE = "X-Gateway-Nonce";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USER_AUTHZ_VERSION = "X-User-Authz-Version";
    public static final String HEADER_USER_AUTHZ_GRANTS = "X-User-Authz-Grants";
    public static final String HEADER_USER_AUTHZ_DENIES = "X-User-Authz-Denies";

    private final GatewaySignProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ConcurrentHashMap<String, Long> nonceExpireCache = new ConcurrentHashMap<>();
    private final AtomicLong cleanupTick = new AtomicLong();

    public GatewaySignVerifyFilter(GatewaySignProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, null);
    }

    public GatewaySignVerifyFilter(GatewaySignProperties properties, ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !pathMatcher.match("/api/**", request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String timestampHeader = request.getHeader(HEADER_GATEWAY_TS);
        String signHeader = request.getHeader(HEADER_GATEWAY_SIGN);
        String nonceHeader = request.getHeader(HEADER_GATEWAY_NONCE);
        if (!StringUtils.hasText(timestampHeader) || !StringUtils.hasText(signHeader)) {
            recordVerify("reject", "missing_sign_headers");
            unauthorized(response, "缺少网关签名头");
            return;
        }
        if (!StringUtils.hasText(nonceHeader)) {
            recordVerify("reject", "missing_nonce");
            unauthorized(response, "缺少网关防重放头");
            return;
        }
        if (nonceHeader.length() < 8 || nonceHeader.length() > 128) {
            recordVerify("reject", "invalid_nonce");
            unauthorized(response, "网关防重放头格式错误");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            recordVerify("reject", "bad_timestamp");
            unauthorized(response, "网关时间戳格式错误");
            return;
        }

        long nowMillis = Instant.now().toEpochMilli();
        long maxSkewMillis = properties.getMaxSkewSeconds() * 1000L;
        if (Math.abs(nowMillis - timestamp) > maxSkewMillis) {
            recordVerify("reject", "expired");
            unauthorized(response, "网关签名已过期");
            return;
        }

        String userId = request.getHeader(HEADER_USER_ID);
        String userRoles = request.getHeader(HEADER_USER_ROLES);
        String canonical = GatewaySignUtils.buildCanonicalString(
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                userId,
                userRoles,
                request.getHeader(HEADER_USER_AUTHZ_VERSION),
                request.getHeader(HEADER_USER_AUTHZ_GRANTS),
                request.getHeader(HEADER_USER_AUTHZ_DENIES),
                timestampHeader,
                nonceHeader
        );

        String expected = GatewaySignUtils.hmacSha256Hex(properties.getInternalSecret(), canonical);
        if (!GatewaySignUtils.constantTimeEquals(expected, signHeader)) {
            recordVerify("reject", "bad_sign");
            unauthorized(response, "网关签名校验失败");
            return;
        }
        if (properties.isReplayProtectionEnabled()
                && isReplayNonce(nonceHeader, nowMillis, maxSkewMillis)) {
            recordVerify("reject", "replay");
            unauthorized(response, "网关请求疑似重放");
            return;
        }

        if (StringUtils.hasText(userId)) {
            List<SimpleGrantedAuthority> authorities = parseAuthorities(userRoles);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        recordVerify("accept", "ok");
        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> parseAuthorities(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return List.of();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Object> result = Result.failure(CommonErrorCode.UNAUTHORIZED.code(), message, null);
        try {
            response.getWriter().write(objectMapper.writeValueAsString(result));
        } catch (JacksonException e) {
            response.getWriter().write("{\"code\":10003,\"message\":\"未认证或认证已过期\",\"data\":null,\"timestamp\":0}");
        }
    }

    private boolean isReplayNonce(String nonce, long nowMillis, long maxSkewMillis) {
        long expireAt = nowMillis + maxSkewMillis;
        final boolean[] replay = {false};
        nonceExpireCache.compute(nonce, (key, currentExpireAt) -> {
            if (currentExpireAt == null || currentExpireAt <= nowMillis) {
                return expireAt;
            }
            replay[0] = true;
            return currentExpireAt;
        });

        long tick = cleanupTick.incrementAndGet();
        if ((tick & 0xFF) == 0) {
            cleanupExpiredNonce(nowMillis);
        }
        trimNonceCache(nowMillis);
        return replay[0];
    }

    private void cleanupExpiredNonce(long nowMillis) {
        nonceExpireCache.entrySet().removeIf(entry -> entry.getValue() <= nowMillis);
    }

    private void trimNonceCache(long nowMillis) {
        int maxEntries = Math.max(properties.getReplayCacheMaxEntries(), 1024);
        if (nonceExpireCache.size() <= maxEntries) {
            return;
        }
        cleanupExpiredNonce(nowMillis);
        if (nonceExpireCache.size() <= maxEntries) {
            return;
        }
        int trimmed = 0;
        int trimTarget = nonceExpireCache.size() - maxEntries;
        for (Map.Entry<String, Long> entry : nonceExpireCache.entrySet()) {
            if (trimmed >= trimTarget) {
                break;
            }
            if (nonceExpireCache.remove(entry.getKey(), entry.getValue())) {
                trimmed++;
            }
        }
    }

    private void recordVerify(String result, String reason) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "shiori_gateway_sign_verify_total",
                "result", result,
                "reason", reason
        ).increment();
    }
}
