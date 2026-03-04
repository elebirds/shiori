package moe.hhm.shiori.user.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.auth.config.UserSecurityProperties;
import moe.hhm.shiori.user.auth.dto.AuthUserInfo;
import moe.hhm.shiori.user.auth.dto.RegisterResponse;
import moe.hhm.shiori.user.auth.dto.TokenPairResponse;
import moe.hhm.shiori.user.auth.model.RegisterUserEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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

    @Mock
    private UserSecurityProperties userSecurityProperties;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldLoginSuccess() {
        UserAuthRecord record = new UserAuthRecord(1L, "U202603030001", "alice", "$2a$hash", 1, 0, null, 0, 0);
        TokenPairResponse response = new TokenPairResponse(
                "access", 900, "refresh", 604800, "Bearer",
                new AuthUserInfo(1L, "U202603030001", "alice", List.of("ROLE_USER"), false)
        );
        when(authUserMapper.findByUsername("alice")).thenReturn(record);
        when(passwordEncoder.matches("pwd", "$2a$hash")).thenReturn(true);
        when(authUserMapper.findRolesByUserId(1L)).thenReturn(List.of("ROLE_USER"));
        when(tokenService.issueTokenPair(1L, "U202603030001", "alice", List.of("ROLE_USER"), false)).thenReturn(response);

        TokenPairResponse actual = authService.login("alice", "pwd", "127.0.0.1");

        assertThat(actual).isEqualTo(response);
        verify(authUserMapper).markLoginSuccess(1L, "127.0.0.1");
        verify(authUserMapper, never()).recordLoginFailure(eq(1L), anyInt(), any());
    }

    @Test
    void shouldRejectWrongPassword() {
        UserAuthRecord record = new UserAuthRecord(1L, "U202603030001", "alice", "$2a$hash", 1, 0, null, 0, 0);
        when(authUserMapper.findByUsername("alice")).thenReturn(record);
        when(passwordEncoder.matches("bad", "$2a$hash")).thenReturn(false);
        when(userSecurityProperties.getLoginFailLockThreshold()).thenReturn(5);
        when(userSecurityProperties.getLockMinutes()).thenReturn(15L);

        assertThatThrownBy(() -> authService.login("alice", "bad", "127.0.0.1"))
                .isInstanceOf(BizException.class);

        verify(authUserMapper).recordLoginFailure(eq(1L), eq(5), any(LocalDateTime.class));
    }

    @Test
    void shouldRejectDisabledAccount() {
        UserAuthRecord record = new UserAuthRecord(1L, "U202603030001", "alice", "$2a$hash", 2, 0, null, 0, 0);
        when(authUserMapper.findByUsername("alice")).thenReturn(record);

        assertThatThrownBy(() -> authService.login("alice", "pwd", "127.0.0.1"))
                .isInstanceOf(BizException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void shouldRejectLockedAccount() {
        UserAuthRecord record = new UserAuthRecord(
                1L, "U202603030001", "alice", "$2a$hash", 3, 0, LocalDateTime.now().plusHours(1), 0, 0
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
                new AuthUserInfo(1L, "U202603030001", "alice", List.of("ROLE_USER"), false)
        );
        when(tokenService.refresh("r1")).thenReturn(response);

        TokenPairResponse actual = authService.refresh("r1");
        authService.logout("r1");

        assertThat(actual.accessToken()).isEqualTo("access");
        verify(tokenService).refresh("r1");
        verify(tokenService).logout("r1");
    }

    @Test
    void shouldChangePasswordSuccess() {
        UserAuthRecord record = new UserAuthRecord(1L, "U202603030001", "alice", "$2a$old", 1, 0, null, 0, 0);
        when(authUserMapper.findById(1L)).thenReturn(record);
        when(passwordEncoder.matches("oldPwd", "$2a$old")).thenReturn(true);
        when(passwordEncoder.encode("newPwd123")).thenReturn("$2a$new");

        authService.changePassword(1L, "oldPwd", "newPwd123");

        verify(authUserMapper).updatePasswordHashById(1L, "$2a$new");
        verify(tokenService).revokeAllSessionsByUserId(1L);
    }

    @Test
    void shouldRejectChangePasswordWhenOldPasswordWrong() {
        UserAuthRecord record = new UserAuthRecord(1L, "U202603030001", "alice", "$2a$old", 1, 0, null, 0, 0);
        when(authUserMapper.findById(1L)).thenReturn(record);
        when(passwordEncoder.matches("wrong", "$2a$old")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1L, "wrong", "newPwd123"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRegisterSuccess() {
        when(authUserMapper.countByUsername("alice")).thenReturn(0);
        when(authUserMapper.findRoleIdByCode("ROLE_USER")).thenReturn(10L);
        when(passwordEncoder.encode("newPwd123")).thenReturn("$2a$new");
        doAnswer(invocation -> {
            RegisterUserEntity user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(authUserMapper).insertUser(any(RegisterUserEntity.class));

        RegisterResponse response = authService.register("alice", "newPwd123", "Alice");

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.nickname()).isEqualTo("Alice");
        verify(authUserMapper).insertUserRole(eq(1L), eq(10L));
    }

    @Test
    void shouldRejectRegisterWhenUsernameExists() {
        when(authUserMapper.countByUsername("alice")).thenReturn(1);

        assertThatThrownBy(() -> authService.register("alice", "newPwd123", "Alice"))
                .isInstanceOf(BizException.class);

        verify(authUserMapper, never()).insertUser(any(RegisterUserEntity.class));
    }
}
