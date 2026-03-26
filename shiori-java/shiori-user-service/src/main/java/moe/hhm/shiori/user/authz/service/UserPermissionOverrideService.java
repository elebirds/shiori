package moe.hhm.shiori.user.authz.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.admin.repository.AdminUserMapper;
import moe.hhm.shiori.user.authz.dto.AdminUserPermissionOverrideResponse;
import moe.hhm.shiori.user.authz.dto.AdminUserPermissionOverrideUpsertRequest;
import moe.hhm.shiori.user.authz.model.PermissionOverrideEffect;
import moe.hhm.shiori.user.authz.model.UserPermissionOverrideRecord;
import moe.hhm.shiori.user.authz.repository.UserAuthzMapper;
import moe.hhm.shiori.user.config.UserMqProperties;
import moe.hhm.shiori.user.config.UserOutboxProperties;
import moe.hhm.shiori.user.event.EventEnvelope;
import moe.hhm.shiori.user.event.UserAuthzChangedPayload;
import moe.hhm.shiori.user.outbox.model.UserOutboxEventEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class UserPermissionOverrideService {

    private static final String OUTBOX_AGGREGATE_TYPE_USER = "user";
    private static final String EVENT_PERMISSION_OVERRIDE_CHANGED = "UserPermissionOverrideChanged";

    private final UserAuthzMapper userAuthzMapper;
    private final AdminUserMapper adminUserMapper;
    private final AuthzSnapshotService authzSnapshotService;
    private final ObjectMapper objectMapper;
    private final UserMqProperties userMqProperties;
    private final UserOutboxProperties userOutboxProperties;
    @Nullable
    private final AuthzEventPublisher authzEventPublisher;

    public UserPermissionOverrideService(UserAuthzMapper userAuthzMapper,
                                         AdminUserMapper adminUserMapper,
                                         AuthzSnapshotService authzSnapshotService,
                                         ObjectMapper objectMapper,
                                         UserMqProperties userMqProperties,
                                         UserOutboxProperties userOutboxProperties,
                                         @Nullable AuthzEventPublisher authzEventPublisher) {
        this.userAuthzMapper = userAuthzMapper;
        this.adminUserMapper = adminUserMapper;
        this.authzSnapshotService = authzSnapshotService;
        this.objectMapper = objectMapper;
        this.userMqProperties = userMqProperties;
        this.userOutboxProperties = userOutboxProperties;
        this.authzEventPublisher = authzEventPublisher;
    }

    public List<AdminUserPermissionOverrideResponse> listOverrides(Long userId) {
        requireUser(userId);
        return userAuthzMapper.listOverridesByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserPermissionOverrideResponse createOverride(Long operatorUserId,
                                                              Long userId,
                                                              AdminUserPermissionOverrideUpsertRequest request) {
        requireUser(userId);
        String permissionCode = normalizePermissionCode(request.permissionCode());
        PermissionOverrideEffect effect = parseEffect(request.effect());
        LocalDateTime startAt = request.startAt();
        LocalDateTime endAt = request.endAt();
        validateTimeWindow(startAt, endAt);

        int affected;
        try {
            affected = userAuthzMapper.insertOverride(
                    userId,
                    permissionCode,
                    effect.name(),
                    startAt,
                    endAt,
                    normalizeReason(request.reason()),
                    operatorUserId
            );
        } catch (DuplicateKeyException ex) {
            throw new BizException(UserErrorCode.PERMISSION_OVERRIDE_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }
        if (affected <= 0) {
            throw new BizException(CommonErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        long version = authzSnapshotService.bumpVersion(userId);
        appendPermissionOverrideChangedOutbox(userId, version, operatorUserId, request.reason());
        publishAuthzChanged(userId, version, request.reason());

        AdminUserPermissionOverrideResponse response = userAuthzMapper.listOverridesByUserId(userId).stream()
                .filter(item -> permissionCode.equalsIgnoreCase(item.permissionCode()))
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new BizException(CommonErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR));

        insertAudit(operatorUserId, userId, "USER_PERMISSION_OVERRIDE_CREATE", "{}", toJson(response), request.reason());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserPermissionOverrideResponse updateOverride(Long operatorUserId,
                                                              Long userId,
                                                              Long overrideId,
                                                              AdminUserPermissionOverrideUpsertRequest request) {
        requireUser(userId);
        UserPermissionOverrideRecord before = requireOverride(userId, overrideId);

        String permissionCode = normalizePermissionCode(request.permissionCode());
        PermissionOverrideEffect effect = parseEffect(request.effect());
        LocalDateTime startAt = request.startAt();
        LocalDateTime endAt = request.endAt();
        validateTimeWindow(startAt, endAt);

        int affected;
        try {
            affected = userAuthzMapper.updateOverride(
                    userId,
                    overrideId,
                    permissionCode,
                    effect.name(),
                    startAt,
                    endAt,
                    normalizeReason(request.reason()),
                    operatorUserId
            );
        } catch (DuplicateKeyException ex) {
            throw new BizException(UserErrorCode.PERMISSION_OVERRIDE_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }
        if (affected <= 0) {
            throw new BizException(CommonErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        long version = authzSnapshotService.bumpVersion(userId);
        appendPermissionOverrideChangedOutbox(userId, version, operatorUserId, request.reason());
        publishAuthzChanged(userId, version, request.reason());

        AdminUserPermissionOverrideResponse response = toResponse(requireOverride(userId, overrideId));
        insertAudit(
                operatorUserId,
                userId,
                "USER_PERMISSION_OVERRIDE_UPDATE",
                toJson(toResponse(before)),
                toJson(response),
                request.reason()
        );
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteOverride(Long operatorUserId,
                               Long userId,
                               Long overrideId,
                               String reason) {
        requireUser(userId);
        UserPermissionOverrideRecord before = requireOverride(userId, overrideId);

        int affected = userAuthzMapper.deleteOverride(userId, overrideId);
        if (affected <= 0) {
            throw new BizException(UserErrorCode.PERMISSION_OVERRIDE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        long version = authzSnapshotService.bumpVersion(userId);
        appendPermissionOverrideChangedOutbox(userId, version, operatorUserId, reason);
        publishAuthzChanged(userId, version, reason);
        insertAudit(
                operatorUserId,
                userId,
                "USER_PERMISSION_OVERRIDE_DELETE",
                toJson(toResponse(before)),
                "{}",
                reason
        );
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0 || userAuthzMapper.countActiveUser(userId) == 0) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
    }

    private UserPermissionOverrideRecord requireOverride(Long userId, Long overrideId) {
        UserPermissionOverrideRecord record = userAuthzMapper.findOverrideById(userId, overrideId);
        if (record == null) {
            throw new BizException(UserErrorCode.PERMISSION_OVERRIDE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return record;
    }

    private PermissionOverrideEffect parseEffect(String raw) {
        try {
            return PermissionOverrideEffect.parse(raw);
        } catch (IllegalArgumentException ex) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizePermissionCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace(':', '.');
    }

    private String normalizeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }

    private void validateTimeWindow(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
    }

    private AdminUserPermissionOverrideResponse toResponse(UserPermissionOverrideRecord record) {
        return new AdminUserPermissionOverrideResponse(
                record.id(),
                record.userId(),
                record.permissionCode(),
                record.effect(),
                record.startAt(),
                record.endAt(),
                record.reason(),
                record.operatorUserId(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private void insertAudit(Long operatorUserId,
                             Long targetUserId,
                             String action,
                             String beforeJson,
                             String afterJson,
                             String reason) {
        adminUserMapper.insertAdminAuditLog(
                operatorUserId,
                targetUserId,
                action,
                beforeJson,
                afterJson,
                normalizeReason(reason)
        );
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private void appendPermissionOverrideChangedOutbox(Long targetUserId,
                                                        long version,
                                                        Long operatorUserId,
                                                        String reason) {
        UserAuthzChangedPayload payload = new UserAuthzChangedPayload(
                targetUserId,
                version,
                Instant.now().toString(),
                normalizeReason(reason),
                operatorUserId
        );
        appendOutbox(
                targetUserId,
                EVENT_PERMISSION_OVERRIDE_CHANGED,
                payload,
                userMqProperties.getUserPermissionOverrideChangedRoutingKey()
        );
    }

    private void appendOutbox(Long targetUserId, String type, Object payload, String routingKey) {
        if (!userOutboxProperties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(routingKey)) {
            return;
        }

        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                type,
                String.valueOf(targetUserId),
                Instant.now().toString(),
                objectMapper.valueToTree(payload)
        );

        String envelopeJson;
        try {
            envelopeJson = objectMapper.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("构建 user outbox 事件失败", e);
        }

        UserOutboxEventEntity entity = new UserOutboxEventEntity();
        entity.setEventId(envelope.eventId());
        entity.setAggregateType(OUTBOX_AGGREGATE_TYPE_USER);
        entity.setAggregateId(envelope.aggregateId());
        entity.setMessageKey(envelope.aggregateId());
        entity.setType(envelope.type());
        entity.setPayload(envelopeJson);
        entity.setExchangeName(userMqProperties.getEventExchange());
        entity.setRoutingKey(routingKey);
        entity.setStatus("PENDING");
        entity.setRetryCount(0);
        adminUserMapper.insertOutboxEvent(entity);
    }

    private void publishAuthzChanged(Long userId, long version, String reason) {
        if (authzEventPublisher == null) {
            return;
        }
        authzEventPublisher.publishAfterCommit(userId, version, reason);
    }
}
