package moe.hhm.shiori.user.admin.service;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.admin.dto.AdminUserAdminRoleUpdateRequest;
import moe.hhm.shiori.user.admin.dto.AdminUserStatusUpdateRequest;
import moe.hhm.shiori.user.admin.model.AdminUserRecord;
import moe.hhm.shiori.user.admin.repository.AdminUserMapper;
import moe.hhm.shiori.user.auth.config.UserSecurityProperties;
import moe.hhm.shiori.user.authz.service.AuthzEventPublisher;
import moe.hhm.shiori.user.authz.service.AuthzSnapshotService;
import moe.hhm.shiori.user.auth.service.TokenService;
import moe.hhm.shiori.user.config.UserMqProperties;
import moe.hhm.shiori.user.config.UserOutboxProperties;
import moe.hhm.shiori.user.outbox.model.UserOutboxEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private AuthzSnapshotService authzSnapshotService;
    @Mock
    private AuthzEventPublisher authzEventPublisher;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        when(userMqProperties.getEventExchange()).thenReturn("shiori.user.event");
        when(userMqProperties.getUserStatusChangedRoutingKey()).thenReturn("user.status.changed");
        when(userMqProperties.getUserRoleChangedRoutingKey()).thenReturn("user.role.changed");
        when(userMqProperties.getUserPasswordResetRoutingKey()).thenReturn("user.password.reset");
        when(userMqProperties.getUserPermissionOverrideChangedRoutingKey()).thenReturn("user.permission-override.changed");
        when(userMqProperties.getUserRoleBindingsChangedRoutingKey()).thenReturn("user.role-bindings.changed");
        when(userOutboxProperties.isEnabled()).thenReturn(true);
        when(userSecurityProperties.getLockMinutes()).thenReturn(15L);
        when(authzSnapshotService.bumpVersion(org.mockito.ArgumentMatchers.anyLong())).thenReturn(2L);
        adminUserService = new AdminUserService(
                adminUserMapper,
                new ObjectMapper(),
                passwordEncoder,
                tokenService,
                userMqProperties,
                userOutboxProperties,
                userSecurityProperties,
                authzSnapshotService,
                authzEventPublisher
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
        ArgumentCaptor<UserOutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(UserOutboxEventEntity.class);
        verify(adminUserMapper, times(2)).insertOutboxEvent(outboxCaptor.capture());
        assertThat(outboxCaptor.getAllValues()).hasSize(2).allSatisfy(event -> {
            assertThat(readStringField(event, "aggregateType")).isEqualTo("user");
            assertThat(readStringField(event, "aggregateId")).isEqualTo("2");
            assertThat(readStringField(event, "messageKey")).isEqualTo("2");
        });
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

    private String readStringField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
