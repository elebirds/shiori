package moe.hhm.shiori.user.auth.service;

import java.time.Duration;
import java.util.List;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.user.auth.config.UserJwtProperties;
import moe.hhm.shiori.user.auth.dto.TokenPairResponse;
import moe.hhm.shiori.user.auth.model.RefreshSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        UserJwtProperties properties = new UserJwtProperties();
        properties.setHmacSecret("test-jwt-secret-test-jwt-secret-123456");
        properties.setIssuer("shiori");
        properties.setAccessTtlSeconds(900);
        properties.setRefreshTtlSeconds(604800);

        tokenService = new TokenService(properties, redisTemplate, new ObjectMapper());
    }

    @Test
    void shouldIssueTokenPairAndStoreOpaqueRefresh() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        TokenPairResponse response = tokenService.issueTokenPair(
                1L, "U202603030001", "alice", List.of("ROLE_USER"), false
        );

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.accessTokenExpiresIn()).isEqualTo(900);
        assertThat(response.refreshTokenExpiresIn()).isEqualTo(604800);

        verify(valueOperations).set(
                anyString(),
                anyString(),
                eq(Duration.ofSeconds(604800))
        );
    }

    @Test
    void shouldRotateRefreshTokenOnRefresh() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        RefreshSession session = new RefreshSession(
                1L, "U202603030001", "alice", List.of("ROLE_USER"), false, System.currentTimeMillis()
        );
        String oldRefresh = "old-refresh-token";
        String oldKey = "auth:refresh:" + GatewaySignUtils.sha256Hex(oldRefresh);
        when(valueOperations.get(oldKey)).thenReturn(new ObjectMapper().writeValueAsString(session));

        TokenPairResponse response = tokenService.refresh(oldRefresh);

        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotEqualTo(oldRefresh);
        assertThat(response.user().userId()).isEqualTo(1L);

        verify(redisTemplate).delete(oldKey);
        verify(valueOperations, atLeastOnce()).set(
                anyString(),
                anyString(),
                eq(Duration.ofSeconds(604800))
        );
    }

    @Test
    void shouldRejectInvalidRefreshToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> tokenService.refresh("missing-token"))
                .isInstanceOf(BizException.class);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void shouldDeleteRefreshSessionOnLogout() {
        String refresh = "refresh-token";
        String expectedKey = "auth:refresh:" + GatewaySignUtils.sha256Hex(refresh);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get(expectedKey)).thenReturn(null);

        tokenService.logout(refresh);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).delete(captor.capture());
        assertThat(captor.getValue()).isEqualTo(expectedKey);
    }
}
