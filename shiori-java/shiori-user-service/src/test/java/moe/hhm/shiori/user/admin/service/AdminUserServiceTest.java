package moe.hhm.shiori.user.admin.service;

import java.time.LocalDateTime;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.admin.dto.AdminUserAdminRoleUpdateRequest;
import moe.hhm.shiori.user.admin.dto.AdminUserStatusUpdateRequest;
import moe.hhm.shiori.user.admin.model.AdminUserRecord;
import moe.hhm.shiori.user.admin.repository.AdminUserMapper;
import moe.hhm.shiori.user.auth.config.UserSecurityProperties;
import moe.hhm.shiori.user.auth.service.TokenService;
import moe.hhm.shiori.user.config.UserMqProperties;
import moe.hhm.shiori.user.config.UserOutboxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserMqProperties userMqProperties;

    @Mock
    private UserOutboxProperties userOutboxProperties;

    @Mock
    private UserSecurityProperties userSecurityProperties;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        when(userMqProperties.getEventExchange()).thenReturn("shiori.user.event");
        when(userMqProperties.getUserStatusChangedRoutingKey()).thenReturn("user.status.changed");
        when(userMqProperties.getUserRoleChangedRoutingKey()).thenReturn("user.role.changed");
        when(userMqProperties.getUserPasswordResetRoutingKey()).thenReturn("user.password.reset");
        when(userMqProperties.isEnabled()).thenReturn(true);
        when(userOutboxProperties.isEnabled()).thenReturn(true);
        when(userSecurityProperties.getLockMinutes()).thenReturn(15L);
        adminUserService = new AdminUserService(
                adminUserMapper,
                new ObjectMapper(),
                passwordEncoder,
                tokenService,
                userMqProperties,
                userOutboxProperties,
                userSecurityProperties
        );
    }

    @Test
    void shouldRejectDisableSelf() {
        when(adminUserMapper.findByUserId(1L)).thenReturn(userRecord(1L, 1, "ROLE_USER,ROLE_ADMIN"));

        assertThrows(BizException.class, () -> adminUserService.updateUserStatus(
                1L,
                1L,
                new AdminUserStatusUpdateRequest("DISABLED", "self")
        ));
    }

    @Test
    void shouldRejectRevokeSelfAdmin() {
        when(adminUserMapper.findByUserId(1L)).thenReturn(userRecord(1L, 1, "ROLE_USER,ROLE_ADMIN"));

        assertThrows(BizException.class, () -> adminUserService.updateAdminRole(
                1L,
                1L,
                new AdminUserAdminRoleUpdateRequest(false, "self")
        ));
    }

    @Test
    void shouldRejectDisableLastEnabledAdmin() {
        when(adminUserMapper.findByUserId(2L)).thenReturn(userRecord(2L, 1, "ROLE_ADMIN"));
        when(adminUserMapper.countEnabledAdminUsers()).thenReturn(1L);

        assertThrows(BizException.class, () -> adminUserService.updateUserStatus(
                9L,
                2L,
                new AdminUserStatusUpdateRequest("DISABLED", "disable")
        ));
        verify(adminUserMapper, never()).updateUserStatus(2L, 2);
    }

    @Test
    void shouldGrantAdminRole() {
        AdminUserRecord before = userRecord(2L, 1, "ROLE_USER");
        AdminUserRecord after = userRecord(2L, 1, "ROLE_USER,ROLE_ADMIN");
        when(adminUserMapper.findByUserId(2L)).thenReturn(before, after);
        when(adminUserMapper.findRoleIdByCode("ROLE_ADMIN")).thenReturn(11L);

        var response = adminUserService.updateAdminRole(
                9L,
                2L,
                new AdminUserAdminRoleUpdateRequest(true, "grant")
        );

        assertTrue(response.admin());
        verify(adminUserMapper).addUserRole(2L, 11L);
    }

    private AdminUserRecord userRecord(Long userId, Integer status, String roleCodes) {
        return new AdminUserRecord(
                userId,
                "U" + userId,
                "user" + userId,
                "nick" + userId,
                null,
                status,
                0,
                null,
                0,
                null,
                null,
                roleCodes,
                LocalDateTime.now(),
                LocalDateTime.now(),
                0
        );
    }
}
