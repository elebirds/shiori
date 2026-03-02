package moe.hhm.shiori.user.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.auth.dto.AuthUserInfo;
import moe.hhm.shiori.user.auth.dto.TokenPairResponse;
import moe.hhm.shiori.user.auth.model.UserAuthRecord;
import moe.hhm.shiori.user.auth.repository.AuthUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthUserMapper authUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldLoginSuccess() {
        UserAuthRecord record = new UserAuthRecord(1L, "U202603030001", "alice", "$2a$hash", 1, 0, null, 0);
        TokenPairResponse response = new TokenPairResponse(
                "access", 900, "refresh", 604800, "Bearer",
                new AuthUserInfo(1L, "U202603030001", "alice", List.of("ROLE_USER"))
        );
        when(authUserMapper.findByUsername("alice")).thenReturn(record);
        when(passwordEncoder.matches("pwd", "$2a$hash")).thenReturn(true);
        when(authUserMapper.findRolesByUserId(1L)).thenReturn(List.of("ROLE_USER"));
        when(tokenService.issueTokenPair(1L, "U202603030001", "alice", List.of("ROLE_USER"))).thenReturn(response);

        TokenPairResponse actual = authService.login("alice", "pwd", "127.0.0.1");

        assertThat(actual).isEqualTo(response);
        verify(authUserMapper).markLoginSuccess(1L, "127.0.0.1");
        verify(authUserMapper, never()).increaseFailedLoginCount(1L);
    }

    @Test
    void shouldRejectWrongPassword() {
        UserAuthRecord record = new UserAuthRecord(1L, "U202603030001", "alice", "$2a$hash", 1, 0, null, 0);
        when(authUserMapper.findByUsername("alice")).thenReturn(record);
        when(passwordEncoder.matches("bad", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "bad", "127.0.0.1"))
                .isInstanceOf(BizException.class);

        verify(authUserMapper).increaseFailedLoginCount(1L);
    }

    @Test
    void shouldRejectDisabledAccount() {
        UserAuthRecord record = new UserAuthRecord(1L, "U202603030001", "alice", "$2a$hash", 2, 0, null, 0);
        when(authUserMapper.findByUsername("alice")).thenReturn(record);

        assertThatThrownBy(() -> authService.login("alice", "pwd", "127.0.0.1"))
                .isInstanceOf(BizException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void shouldRejectLockedAccount() {
        UserAuthRecord record = new UserAuthRecord(
                1L, "U202603030001", "alice", "$2a$hash", 3, 0, LocalDateTime.now().plusHours(1), 0
        );
        when(authUserMapper.findByUsername("alice")).thenReturn(record);

        assertThatThrownBy(() -> authService.login("alice", "pwd", "127.0.0.1"))
                .isInstanceOf(BizException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void shouldRefreshAndLogoutByTokenService() {
        TokenPairResponse response = new TokenPairResponse(
                "access", 900, "refresh", 604800, "Bearer",
                new AuthUserInfo(1L, "U202603030001", "alice", List.of("ROLE_USER"))
        );
        when(tokenService.refresh("r1")).thenReturn(response);

        TokenPairResponse actual = authService.refresh("r1");
        authService.logout("r1");

        assertThat(actual.accessToken()).isEqualTo("access");
        verify(tokenService).refresh("r1");
        verify(tokenService).logout("r1");
    }
}
