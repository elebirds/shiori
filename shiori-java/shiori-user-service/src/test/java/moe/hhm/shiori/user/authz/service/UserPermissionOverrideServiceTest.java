package moe.hhm.shiori.user.authz.service;

import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.admin.repository.AdminUserMapper;
import moe.hhm.shiori.user.authz.dto.AdminUserPermissionOverrideUpsertRequest;
import moe.hhm.shiori.user.authz.repository.UserAuthzMapper;
import moe.hhm.shiori.user.config.UserMqProperties;
import moe.hhm.shiori.user.config.UserOutboxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPermissionOverrideServiceTest {

    @Mock
    private UserAuthzMapper userAuthzMapper;
    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private AuthzSnapshotService authzSnapshotService;
    @Mock
    private UserMqProperties userMqProperties;
    @Mock
    private UserOutboxProperties userOutboxProperties;

    private UserPermissionOverrideService userPermissionOverrideService;

    @BeforeEach
    void setUp() {
        userPermissionOverrideService = new UserPermissionOverrideService(
                userAuthzMapper,
                adminUserMapper,
                authzSnapshotService,
                new ObjectMapper(),
                userMqProperties,
                userOutboxProperties
        );
    }

    @Test
    void shouldReturnConflictWhenCreateOverrideDuplicate() {
        when(userAuthzMapper.countActiveUser(1L)).thenReturn(1);
        when(userAuthzMapper.insertOverride(
                1L,
                "chat.send",
                "DENY",
                null,
                null,
                "duplicate",
                99L
        )).thenThrow(new DuplicateKeyException("duplicate"));

        BizException ex = assertThrows(BizException.class, () -> userPermissionOverrideService.createOverride(
                99L,
                1L,
                new AdminUserPermissionOverrideUpsertRequest("chat.send", "DENY", null, null, "duplicate")
        ));
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals(UserErrorCode.PERMISSION_OVERRIDE_ALREADY_EXISTS.code(), ex.getErrorCode().code());
    }
}
