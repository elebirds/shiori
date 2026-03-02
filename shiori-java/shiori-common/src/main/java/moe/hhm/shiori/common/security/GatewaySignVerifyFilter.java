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
import java.util.stream.Collectors;
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
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private final GatewaySignProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewaySignVerifyFilter(GatewaySignProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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
        if (!StringUtils.hasText(timestampHeader) || !StringUtils.hasText(signHeader)) {
            unauthorized(response, "缺少网关签名头");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            unauthorized(response, "网关时间戳格式错误");
            return;
        }

        long nowMillis = Instant.now().toEpochMilli();
        long maxSkewMillis = properties.getMaxSkewSeconds() * 1000L;
        if (Math.abs(nowMillis - timestamp) > maxSkewMillis) {
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
                timestampHeader
        );

        String expected = GatewaySignUtils.hmacSha256Hex(properties.getInternalSecret(), canonical);
        if (!GatewaySignUtils.constantTimeEquals(expected, signHeader)) {
            unauthorized(response, "网关签名校验失败");
            return;
        }

        if (StringUtils.hasText(userId)) {
            List<SimpleGrantedAuthority> authorities = parseAuthorities(userRoles);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

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
}
