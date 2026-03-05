package moe.hhm.shiori.user.admin.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.admin.dto.AdminRoleResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserAdminRoleUpdateRequest;
import moe.hhm.shiori.user.admin.dto.AdminUserAuditItemResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserAuditPageResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserCapabilityBanResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserCapabilityBanUpsertRequest;
import moe.hhm.shiori.user.admin.dto.AdminUserDetailResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserLockRequest;
import moe.hhm.shiori.user.admin.dto.AdminUserPasswordResetRequest;
import moe.hhm.shiori.user.admin.dto.AdminUserPageResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserStatusResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserStatusUpdateRequest;
import moe.hhm.shiori.user.admin.dto.AdminUserSummaryResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserUnlockRequest;
import moe.hhm.shiori.user.admin.model.AdminUserAuditRecord;
import moe.hhm.shiori.user.admin.model.AdminUserCapabilityBanRecord;
import moe.hhm.shiori.user.admin.model.AdminUserRecord;
import moe.hhm.shiori.user.admin.model.UserCapability;
import moe.hhm.shiori.user.admin.repository.AdminUserMapper;
import moe.hhm.shiori.user.authz.service.AuthzSnapshotService;
import moe.hhm.shiori.user.auth.service.TokenService;
import moe.hhm.shiori.user.config.UserMqProperties;
import moe.hhm.shiori.user.config.UserOutboxProperties;
import moe.hhm.shiori.user.domain.UserStatus;
import moe.hhm.shiori.user.event.EventEnvelope;
import moe.hhm.shiori.user.event.UserAuthzChangedPayload;
import moe.hhm.shiori.user.event.UserPasswordResetPayload;
import moe.hhm.shiori.user.event.UserRoleChangedPayload;
import moe.hhm.shiori.user.event.UserStatusChangedPayload;
import moe.hhm.shiori.user.outbox.model.UserOutboxEventEntity;
import moe.hhm.shiori.user.auth.config.UserSecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminUserService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String EVENT_USER_STATUS_CHANGED = "UserStatusChanged";
    private static final String EVENT_USER_ROLE_CHANGED = "UserRoleChanged";
    private static final String EVENT_USER_PASSWORD_RESET = "UserPasswordReset";
    private static final String EVENT_USER_ROLE_BINDINGS_CHANGED = "UserRoleBindingsChanged";
    private static final String EVENT_USER_PERMISSION_OVERRIDE_CHANGED = "UserPermissionOverrideChanged";

    private final AdminUserMapper adminUserMapper;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserMqProperties userMqProperties;
    private final UserOutboxProperties userOutboxProperties;
    private final UserSecurityProperties userSecurityProperties;
    @Nullable
    private final AuthzSnapshotService authzSnapshotService;

    public AdminUserService(AdminUserMapper adminUserMapper,
                            ObjectMapper objectMapper,
                            PasswordEncoder passwordEncoder,
                            TokenService tokenService,
                            UserMqProperties userMqProperties,
                            UserOutboxProperties userOutboxProperties,
                            UserSecurityProperties userSecurityProperties,
                            @Nullable AuthzSnapshotService authzSnapshotService) {
        this.adminUserMapper = adminUserMapper;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userMqProperties = userMqProperties;
        this.userOutboxProperties = userOutboxProperties;
        this.userSecurityProperties = userSecurityProperties;
        this.authzSnapshotService = authzSnapshotService;
    }

    public AdminUserPageResponse listUsers(String keyword, String status, String role, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        Integer statusCode = parseStatusCode(status, false);
        String normalizedRole = StringUtils.hasText(role) ? role.trim().toUpperCase() : null;
        int offset = (normalizedPage - 1) * normalizedSize;

        long total = adminUserMapper.countUsers(keyword, statusCode, normalizedRole);
        List<AdminUserRecord> records = adminUserMapper.listUsers(keyword, statusCode, normalizedRole, normalizedSize, offset);
        List<AdminUserSummaryResponse> items = records.stream().map(this::toSummary).toList();
        return new AdminUserPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public AdminUserDetailResponse getUserDetail(Long userId) {
        AdminUserRecord record = requireUser(userId);
        return toDetail(record);
    }

    public AdminUserAuditPageResponse listUserAudits(Long userId, String action, int page, int size) {
        requireUser(userId);

        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedAction = StringUtils.hasText(action) ? action.trim().toUpperCase() : null;

        long total = adminUserMapper.countAdminAudits(userId, normalizedAction);
        List<AdminUserAuditRecord> records = adminUserMapper.listAdminAudits(userId, normalizedAction, normalizedSize, offset);
        List<AdminUserAuditItemResponse> items = records.stream()
                .map(record -> new AdminUserAuditItemResponse(
                        record.id(),
                        record.operatorUserId(),
                        record.targetUserId(),
                        record.action(),
                        record.beforeJson(),
                        record.afterJson(),
                        record.reason(),
                        record.createdAt()
                ))
                .toList();
        return new AdminUserAuditPageResponse(total, normalizedPage, normalizedSize, items);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserStatusResponse updateUserStatus(Long operatorUserId,
                                                    Long targetUserId,
                                                    AdminUserStatusUpdateRequest request) {
        AdminUserRecord before = requireUser(targetUserId);
        UserStatus currentStatus = resolveStatus(before.status());
        UserStatus targetStatus = resolveStatusForUpdate(request.status());

        if (operatorUserId.equals(targetUserId) && targetStatus == UserStatus.DISABLED) {
            throw new BizException(UserErrorCode.CANNOT_DISABLE_SELF, HttpStatus.BAD_REQUEST);
        }

        boolean targetHasAdmin = hasAdminRole(before.roleCodes());
        if (targetHasAdmin && currentStatus == UserStatus.ENABLED && targetStatus == UserStatus.DISABLED) {
            ensureAtLeastOneEnabledAdmin();
        }

        if (currentStatus != targetStatus) {
            adminUserMapper.updateUserStatus(targetUserId, targetStatus.getCode());
            appendStatusChangedOutbox(
                    targetUserId,
                    currentStatus.name(),
                    targetStatus.name(),
                    operatorUserId,
                    request.reason()
            );
        }

        AdminUserRecord after = requireUser(targetUserId);
        insertAudit(
                operatorUserId,
                targetUserId,
                "USER_STATUS_UPDATE",
                stateSnapshot(before),
                stateSnapshot(after),
                request.reason()
        );
        return new AdminUserStatusResponse(after.userId(), resolveStatus(after.status()).name(), hasAdminRole(after.roleCodes()));
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserStatusResponse updateAdminRole(Long operatorUserId,
                                                   Long targetUserId,
                                                   AdminUserAdminRoleUpdateRequest request) {
        AdminUserRecord before = requireUser(targetUserId);
        boolean beforeAdmin = hasAdminRole(before.roleCodes());

        boolean grantAdmin = Boolean.TRUE.equals(request.grantAdmin());

        if (!grantAdmin && operatorUserId.equals(targetUserId)) {
            throw new BizException(UserErrorCode.CANNOT_REVOKE_SELF_ADMIN, HttpStatus.BAD_REQUEST);
        }

        Long adminRoleId = adminUserMapper.findRoleIdByCode(ROLE_ADMIN);
        if (adminRoleId == null) {
            throw new BizException(UserErrorCode.ROLE_NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (grantAdmin) {
            if (!beforeAdmin) {
                adminUserMapper.addUserRole(targetUserId, adminRoleId);
            }
        } else if (beforeAdmin) {
            if (resolveStatus(before.status()) == UserStatus.ENABLED) {
                ensureAtLeastOneEnabledAdmin();
            }
            adminUserMapper.removeUserRole(targetUserId, adminRoleId);
        }

        AdminUserRecord after = requireUser(targetUserId);
        boolean afterAdmin = hasAdminRole(after.roleCodes());
        insertAudit(
                operatorUserId,
                targetUserId,
                "USER_ADMIN_ROLE_UPDATE",
                stateSnapshot(before),
                stateSnapshot(after),
                request.reason()
        );
        if (beforeAdmin != afterAdmin) {
            appendRoleChangedOutbox(
                    targetUserId,
                    ROLE_ADMIN,
                    afterAdmin,
                    operatorUserId,
                    request.reason()
            );
            long version = bumpAuthzVersion(targetUserId);
            appendRoleBindingsChangedOutbox(targetUserId, version, operatorUserId, request.reason());
        }
        return new AdminUserStatusResponse(after.userId(), resolveStatus(after.status()).name(), afterAdmin);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserStatusResponse lockUser(Long operatorUserId, Long targetUserId, AdminUserLockRequest request) {
        AdminUserRecord before = requireUser(targetUserId);
        if (operatorUserId.equals(targetUserId)) {
            throw new BizException(UserErrorCode.CANNOT_LOCK_SELF, HttpStatus.BAD_REQUEST);
        }

        long durationMinutes = request != null && request.durationMinutes() != null
                ? Math.max(request.durationMinutes(), 1)
                : Math.max(userSecurityProperties.getLockMinutes(), 1);
        LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(durationMinutes);
        adminUserMapper.updateUserLockState(targetUserId, UserStatus.LOCKED.getCode(), lockUntil);

        AdminUserRecord after = requireUser(targetUserId);
        insertAudit(
                operatorUserId,
                targetUserId,
                "USER_LOCK",
                stateSnapshot(before),
                stateSnapshot(after),
                request == null ? null : request.reason()
        );
        appendStatusChangedOutbox(
                targetUserId,
                resolveStatus(before.status()).name(),
                resolveStatus(after.status()).name(),
                operatorUserId,
                request == null ? null : request.reason()
        );
        return new AdminUserStatusResponse(after.userId(), resolveStatus(after.status()).name(), hasAdminRole(after.roleCodes()));
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserStatusResponse unlockUser(Long operatorUserId, Long targetUserId, AdminUserUnlockRequest request) {
        AdminUserRecord before = requireUser(targetUserId);
        boolean isLocked = resolveStatus(before.status()) == UserStatus.LOCKED || before.lockedUntil() != null;
        if (!isLocked) {
            throw new BizException(UserErrorCode.USER_NOT_LOCKED, HttpStatus.CONFLICT);
        }

        adminUserMapper.unlockUser(targetUserId);
        AdminUserRecord after = requireUser(targetUserId);
        insertAudit(
                operatorUserId,
                targetUserId,
                "USER_UNLOCK",
                stateSnapshot(before),
                stateSnapshot(after),
                request == null ? null : request.reason()
        );
        appendStatusChangedOutbox(
                targetUserId,
                resolveStatus(before.status()).name(),
                resolveStatus(after.status()).name(),
                operatorUserId,
                request == null ? null : request.reason()
        );
        return new AdminUserStatusResponse(after.userId(), resolveStatus(after.status()).name(), hasAdminRole(after.roleCodes()));
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserStatusResponse resetPassword(Long operatorUserId,
                                                 Long targetUserId,
                                                 AdminUserPasswordResetRequest request) {
        AdminUserRecord before = requireUser(targetUserId);
        boolean forceChangePassword = request.forceChangePassword() == null || request.forceChangePassword();
        adminUserMapper.updatePasswordByAdmin(
                targetUserId,
                passwordEncoder.encode(request.newPassword()),
                forceChangePassword ? 1 : 0
        );
        tokenService.revokeAllSessionsByUserId(targetUserId);

        AdminUserRecord after = requireUser(targetUserId);
        insertAudit(
                operatorUserId,
                targetUserId,
                "USER_PASSWORD_RESET",
                stateSnapshot(before),
                stateSnapshot(after),
                request.reason()
        );
        appendPasswordResetOutbox(
                targetUserId,
                operatorUserId,
                forceChangePassword,
                request.reason()
        );
        return new AdminUserStatusResponse(after.userId(), resolveStatus(after.status()).name(), hasAdminRole(after.roleCodes()));
    }

    public List<AdminRoleResponse> listRoles() {
        return adminUserMapper.listActiveRoles().stream()
                .map(record -> new AdminRoleResponse(record.roleCode(), record.roleName()))
                .toList();
    }

    public List<AdminUserCapabilityBanResponse> listCapabilityBans(Long userId) {
        requireUser(userId);
        return adminUserMapper.listCapabilityBansByUserId(userId).stream()
                .map(this::toCapabilityBanResponse)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserCapabilityBanResponse upsertCapabilityBan(Long operatorUserId,
                                                              Long targetUserId,
                                                              AdminUserCapabilityBanUpsertRequest request) {
        requireUser(targetUserId);
        UserCapability capability = parseCapability(request.capability());
        LocalDateTime startAt = request.startAt() == null ? LocalDateTime.now() : request.startAt();
        LocalDateTime endAt = request.endAt();
        if (endAt != null && !endAt.isAfter(startAt)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        String beforeSnapshot = capabilityBanSnapshot(targetUserId);
        adminUserMapper.upsertCapabilityBan(
                targetUserId,
                capability.name(),
                1,
                normalizeReason(request.reason()),
                operatorUserId,
                startAt,
                endAt
        );
        String afterSnapshot = capabilityBanSnapshot(targetUserId);
        insertAudit(
                operatorUserId,
                targetUserId,
                "USER_CAPABILITY_BAN_UPSERT",
                beforeSnapshot,
                afterSnapshot,
                request.reason()
        );
        long version = bumpAuthzVersion(targetUserId);
        appendPermissionOverrideChangedOutbox(targetUserId, version, operatorUserId, request.reason());
        return findCapabilityBanResponse(targetUserId, capability.name());
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserCapabilityBanResponse removeCapabilityBan(Long operatorUserId,
                                                              Long targetUserId,
                                                              String capabilityRaw,
                                                              String reason) {
        requireUser(targetUserId);
        UserCapability capability = parseCapability(capabilityRaw);
        String beforeSnapshot = capabilityBanSnapshot(targetUserId);
        int affected = adminUserMapper.disableCapabilityBan(
                targetUserId,
                capability.name(),
                normalizeReason(reason),
                operatorUserId
        );
        if (affected == 0) {
            throw new BizException(UserErrorCode.CAPABILITY_BAN_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        String afterSnapshot = capabilityBanSnapshot(targetUserId);
        insertAudit(
                operatorUserId,
                targetUserId,
                "USER_CAPABILITY_BAN_REMOVE",
                beforeSnapshot,
                afterSnapshot,
                reason
        );
        long version = bumpAuthzVersion(targetUserId);
        appendPermissionOverrideChangedOutbox(targetUserId, version, operatorUserId, reason);
        return findCapabilityBanResponse(targetUserId, capability.name());
    }

    public List<String> listActiveCapabilities(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return adminUserMapper.listActiveCapabilityCodes(userId);
    }

    private AdminUserRecord requireUser(Long userId) {
        AdminUserRecord record = adminUserMapper.findByUserId(userId);
        if (record == null || (record.isDeleted() != null && record.isDeleted() == 1)) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return record;
    }

    private void ensureAtLeastOneEnabledAdmin() {
        long enabledAdmins = adminUserMapper.countEnabledAdminUsers();
        if (enabledAdmins <= 1) {
            throw new BizException(UserErrorCode.ADMIN_MIN_ONE_REQUIRED, HttpStatus.CONFLICT);
        }
    }

    private String stateSnapshot(AdminUserRecord record) {
        Map<String, Object> snapshot = Map.of(
                "status", resolveStatus(record.status()).name()
        );
        Map<String, Object> mutable = new LinkedHashMap<>(snapshot);
        mutable.put("roles", parseRoles(record.roleCodes()));
        mutable.put("lockedUntil", record.lockedUntil() == null ? "" : record.lockedUntil().toString());
        mutable.put("mustChangePassword", record.mustChangePassword() != null && record.mustChangePassword() == 1);
        try {
            return objectMapper.writeValueAsString(mutable);
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private String capabilityBanSnapshot(Long userId) {
        List<AdminUserCapabilityBanRecord> records = adminUserMapper.listCapabilityBansByUserId(userId);
        try {
            return objectMapper.writeValueAsString(records);
        } catch (JacksonException e) {
            return "[]";
        }
    }

    private AdminUserSummaryResponse toSummary(AdminUserRecord record) {
        return new AdminUserSummaryResponse(
                record.userId(),
                record.userNo(),
                record.username(),
                record.nickname(),
                resolveStatus(record.status()).name(),
                parseRoles(record.roleCodes()),
                record.lastLoginAt(),
                record.createdAt()
        );
    }

    private AdminUserDetailResponse toDetail(AdminUserRecord record) {
        return new AdminUserDetailResponse(
                record.userId(),
                record.userNo(),
                record.username(),
                record.nickname(),
                record.avatarUrl(),
                resolveStatus(record.status()).name(),
                record.failedLoginCount(),
                record.lockedUntil(),
                record.mustChangePassword() != null && record.mustChangePassword() == 1,
                record.lastLoginAt(),
                record.lastLoginIp(),
                parseRoles(record.roleCodes()),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private boolean hasAdminRole(String roleCodes) {
        return parseRoles(roleCodes).contains(ROLE_ADMIN);
    }

    private List<String> parseRoles(String roleCodes) {
        if (!StringUtils.hasText(roleCodes)) {
            return List.of();
        }
        String[] parts = roleCodes.split(",");
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                set.add(part.trim().toUpperCase());
            }
        }
        return new ArrayList<>(set);
    }

    private AdminUserCapabilityBanResponse findCapabilityBanResponse(Long userId, String capabilityCode) {
        return adminUserMapper.listCapabilityBansByUserId(userId).stream()
                .filter(item -> capabilityCode.equalsIgnoreCase(item.capabilityCode()))
                .findFirst()
                .map(this::toCapabilityBanResponse)
                .orElseThrow(() -> new BizException(UserErrorCode.CAPABILITY_BAN_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private UserCapability parseCapability(String rawCapability) {
        try {
            return UserCapability.parse(rawCapability);
        } catch (IllegalArgumentException ex) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
    }

    private AdminUserCapabilityBanResponse toCapabilityBanResponse(AdminUserCapabilityBanRecord record) {
        return new AdminUserCapabilityBanResponse(
                record.id(),
                record.userId(),
                record.capabilityCode(),
                record.isBanned() != null && record.isBanned() == 1,
                record.reason(),
                record.operatorUserId(),
                record.startAt(),
                record.endAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private UserStatus resolveStatusForUpdate(String status) {
        Integer statusCode = parseStatusCode(status, true);
        UserStatus resolved = resolveStatus(statusCode);
        if (resolved == UserStatus.LOCKED) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        return resolved;
    }

    private Integer parseStatusCode(String status, boolean required) {
        if (!StringUtils.hasText(status)) {
            if (required) {
                throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = status.trim().toUpperCase();
        try {
            return UserStatus.valueOf(normalized).getCode();
        } catch (IllegalArgumentException ex) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
    }

    private UserStatus resolveStatus(Integer code) {
        if (code == null) {
            return UserStatus.ENABLED;
        }
        for (UserStatus status : UserStatus.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return UserStatus.ENABLED;
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
                StringUtils.hasText(reason) ? reason.trim() : null
        );
    }

    private void appendStatusChangedOutbox(Long targetUserId,
                                           String beforeStatus,
                                           String afterStatus,
                                           Long operatorUserId,
                                           String reason) {
        UserStatusChangedPayload payload = new UserStatusChangedPayload(
                targetUserId,
                beforeStatus,
                afterStatus,
                operatorUserId,
                normalizeReason(reason)
        );
        appendOutbox(targetUserId, EVENT_USER_STATUS_CHANGED, payload, userMqProperties.getUserStatusChangedRoutingKey());
    }

    private void appendRoleChangedOutbox(Long targetUserId,
                                         String roleCode,
                                         boolean granted,
                                         Long operatorUserId,
                                         String reason) {
        UserRoleChangedPayload payload = new UserRoleChangedPayload(
                targetUserId,
                roleCode,
                granted,
                operatorUserId,
                normalizeReason(reason)
        );
        appendOutbox(targetUserId, EVENT_USER_ROLE_CHANGED, payload, userMqProperties.getUserRoleChangedRoutingKey());
    }

    private void appendPasswordResetOutbox(Long targetUserId,
                                           Long operatorUserId,
                                           boolean mustChangePassword,
                                           String reason) {
        UserPasswordResetPayload payload = new UserPasswordResetPayload(
                targetUserId,
                operatorUserId,
                mustChangePassword,
                normalizeReason(reason)
        );
        appendOutbox(targetUserId, EVENT_USER_PASSWORD_RESET, payload, userMqProperties.getUserPasswordResetRoutingKey());
    }

    private void appendRoleBindingsChangedOutbox(Long targetUserId,
                                                 Long version,
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
                EVENT_USER_ROLE_BINDINGS_CHANGED,
                payload,
                userMqProperties.getUserRoleBindingsChangedRoutingKey()
        );
    }

    private void appendPermissionOverrideChangedOutbox(Long targetUserId,
                                                       Long version,
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
                EVENT_USER_PERMISSION_OVERRIDE_CHANGED,
                payload,
                userMqProperties.getUserPermissionOverrideChangedRoutingKey()
        );
    }

    private void appendOutbox(Long targetUserId, String type, Object payload, String routingKey) {
        if (!userOutboxProperties.isEnabled() || !userMqProperties.isEnabled()) {
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
        entity.setAggregateId(envelope.aggregateId());
        entity.setType(envelope.type());
        entity.setPayload(envelopeJson);
        entity.setExchangeName(userMqProperties.getEventExchange());
        entity.setRoutingKey(routingKey);
        entity.setStatus("PENDING");
        entity.setRetryCount(0);
        adminUserMapper.insertOutboxEvent(entity);
    }

    private String normalizeReason(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }

    private long bumpAuthzVersion(Long userId) {
        if (authzSnapshotService == null) {
            return 0L;
        }
        return authzSnapshotService.bumpVersion(userId);
    }
}
