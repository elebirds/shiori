package moe.hhm.shiori.user.authz.service;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.admin.repository.AdminUserMapper;
import moe.hhm.shiori.user.authz.dto.AdminUserPermissionOverrideUpsertRequest;
import moe.hhm.shiori.user.authz.model.UserPermissionOverrideRecord;
import moe.hhm.shiori.user.authz.repository.UserAuthzMapper;
import moe.hhm.shiori.user.config.UserMqProperties;
import moe.hhm.shiori.user.config.UserOutboxProperties;
import moe.hhm.shiori.user.outbox.model.UserOutboxEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
    @Mock
    private AuthzEventPublisher authzEventPublisher;

    private UserPermissionOverrideService userPermissionOverrideService;

    @BeforeEach
    void setUp() {
        lenient().when(userMqProperties.getEventExchange()).thenReturn("shiori.user.event");
        lenient().when(userMqProperties.getUserPermissionOverrideChangedRoutingKey()).thenReturn("user.permission-override.changed");
        lenient().when(userOutboxProperties.isEnabled()).thenReturn(true);
        userPermissionOverrideService = new UserPermissionOverrideService(
                userAuthzMapper,
                adminUserMapper,
                authzSnapshotService,
                new ObjectMapper(),
                userMqProperties,
                userOutboxProperties,
                authzEventPublisher
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

    @Test
    void shouldAppendOutboxWithKafkaMetadataWhenCreateOverride() {
        LocalDateTime now = LocalDateTime.now();
        when(userAuthzMapper.countActiveUser(1L)).thenReturn(1);
        when(userAuthzMapper.insertOverride(
                1L,
                "chat.send",
                "DENY",
                null,
                null,
                "duplicate",
                99L
        )).thenReturn(1);
        when(authzSnapshotService.bumpVersion(1L)).thenReturn(2L);
        when(userAuthzMapper.listOverridesByUserId(1L)).thenReturn(List.of(
                new UserPermissionOverrideRecord(
                        10L,
                        1L,
                        "chat.send",
                        "DENY",
                        "duplicate",
                        99L,
                        null,
                        null,
                        now,
                        now
                )
        ));

        var response = userPermissionOverrideService.createOverride(
                99L,
                1L,
                new AdminUserPermissionOverrideUpsertRequest("chat.send", "DENY", null, null, "duplicate")
        );

        assertThat(response.userId()).isEqualTo(1L);
        ArgumentCaptor<UserOutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(UserOutboxEventEntity.class);
        verify(adminUserMapper).insertOutboxEvent(outboxCaptor.capture());
        UserOutboxEventEntity outbox = outboxCaptor.getValue();
        assertThat(readStringField(outbox, "aggregateType")).isEqualTo("user");
        assertThat(readStringField(outbox, "aggregateId")).isEqualTo("1");
        assertThat(readStringField(outbox, "messageKey")).isEqualTo("1");
        verify(userAuthzMapper).insertOverride(
                eq(1L),
                eq("chat.send"),
                eq("DENY"),
                isNull(),
                isNull(),
                eq("duplicate"),
                eq(99L)
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
