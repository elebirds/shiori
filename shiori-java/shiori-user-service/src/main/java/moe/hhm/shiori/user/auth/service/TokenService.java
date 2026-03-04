package moe.hhm.shiori.user.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.user.auth.config.UserJwtProperties;
import moe.hhm.shiori.user.auth.dto.AuthUserInfo;
import moe.hhm.shiori.user.auth.dto.TokenPairResponse;
import moe.hhm.shiori.user.auth.model.RefreshSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TokenService {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String REFRESH_USER_SET_PREFIX = "auth:refresh:user:";

    private final UserJwtProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TokenService(UserJwtProperties properties, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public TokenPairResponse issueTokenPair(Long userId,
                                            String userNo,
                                            String username,
                                            List<String> roles,
                                            boolean mustChangePassword) {
        String accessToken = generateAccessToken(userId, userNo, username, roles, mustChangePassword);
        String refreshToken = generateOpaqueRefreshToken();
        RefreshSession session = new RefreshSession(
                userId,
                userNo,
                username,
                roles,
                mustChangePassword,
                Instant.now().toEpochMilli()
        );
        putRefreshSession(refreshToken, session);
        return buildResponse(accessToken, refreshToken, session);
    }

    public TokenPairResponse refresh(String refreshToken) {
        RefreshSession session = readRefreshSession(refreshToken);
        if (session == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
        deleteRefreshSession(refreshToken);

        String newRefreshToken = generateOpaqueRefreshToken();
        String newAccessToken = generateAccessToken(
                session.userId(),
                session.userNo(),
                session.username(),
                session.roles(),
                session.mustChangePassword()
        );
        RefreshSession newSession = new RefreshSession(
                session.userId(),
                session.userNo(),
                session.username(),
                session.roles(),
                session.mustChangePassword(),
                Instant.now().toEpochMilli()
        );
        putRefreshSession(newRefreshToken, newSession);
        return buildResponse(newAccessToken, newRefreshToken, newSession);
    }

    public void logout(String refreshToken) {
        deleteRefreshSession(refreshToken);
    }

    public void revokeAllSessionsByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        String setKey = userRefreshSetKey(userId);
        Set<String> hashes = redisTemplate.opsForSet().members(setKey);
        if (hashes != null && !hashes.isEmpty()) {
            Set<String> refreshKeys = hashes.stream()
                    .filter(StringUtils::hasText)
                    .map(this::refreshKeyByHash)
                    .collect(Collectors.toSet());
            if (!refreshKeys.isEmpty()) {
                redisTemplate.delete(refreshKeys);
            }
        }
        redisTemplate.delete(setKey);
    }

    private TokenPairResponse buildResponse(String accessToken, String refreshToken, RefreshSession session) {
        AuthUserInfo userInfo = new AuthUserInfo(
                session.userId(),
                session.userNo(),
                session.username(),
                session.roles(),
                session.mustChangePassword()
        );
        return new TokenPairResponse(
                accessToken,
                properties.getAccessTtlSeconds(),
                refreshToken,
                properties.getRefreshTtlSeconds(),
                "Bearer",
                userInfo
        );
    }

    private String generateAccessToken(Long userId,
                                       String userNo,
                                       String username,
                                       List<String> roles,
                                       boolean mustChangePassword) {
        if (!StringUtils.hasText(properties.getHmacSecret())) {
            throw new IllegalStateException("缺少 security.jwt.hmac-secret 配置");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getAccessTtlSeconds());
        List<String> normalizedRoles = normalizeRoles(roles);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(userId))
                .issuer(properties.getIssuer())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("uid", String.valueOf(userId))
                .claim("userNo", userNo)
                .claim("username", username)
                .claim("roles", normalizedRoles)
                .claim("mustChangePassword", mustChangePassword)
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            signedJWT.sign(new MACSigner(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new IllegalStateException("签发 access token 失败", e);
        }
        return signedJWT.serialize();
    }

    private String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[36];
        ThreadLocalRandom.current().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void putRefreshSession(String refreshToken, RefreshSession session) {
        String refreshTokenHash = refreshTokenHash(refreshToken);
        String key = refreshKeyByHash(refreshTokenHash);
        Duration ttl = Duration.ofSeconds(properties.getRefreshTtlSeconds());
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(session),
                    ttl
            );
            if (session.userId() != null) {
                String setKey = userRefreshSetKey(session.userId());
                redisTemplate.opsForSet().add(setKey, refreshTokenHash);
                redisTemplate.expire(setKey, ttl);
            }
        } catch (JacksonException e) {
            throw new IllegalStateException("写入 refresh token 会话失败", e);
        }
    }

    private RefreshSession readRefreshSession(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(refreshKeyByHash(refreshTokenHash(refreshToken)));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, RefreshSession.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    private void deleteRefreshSession(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        String refreshTokenHash = refreshTokenHash(refreshToken);
        String refreshKey = refreshKeyByHash(refreshTokenHash);
        RefreshSession session = readRefreshSession(refreshToken);
        redisTemplate.delete(refreshKey);
        if (session != null && session.userId() != null) {
            redisTemplate.opsForSet().remove(userRefreshSetKey(session.userId()), refreshTokenHash);
        }
    }

    private String refreshTokenHash(String refreshToken) {
        return GatewaySignUtils.sha256Hex(refreshToken);
    }

    private String refreshKeyByHash(String refreshTokenHash) {
        return REFRESH_KEY_PREFIX + refreshTokenHash;
    }

    private String userRefreshSetKey(Long userId) {
        return REFRESH_USER_SET_PREFIX + userId;
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toUpperCase)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }
}
