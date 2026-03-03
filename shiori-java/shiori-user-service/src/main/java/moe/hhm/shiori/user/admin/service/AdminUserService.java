package moe.hhm.shiori.user.admin.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.admin.dto.AdminRoleResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserAdminRoleUpdateRequest;
import moe.hhm.shiori.user.admin.dto.AdminUserDetailResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserPageResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserStatusResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserStatusUpdateRequest;
import moe.hhm.shiori.user.admin.dto.AdminUserSummaryResponse;
import moe.hhm.shiori.user.admin.model.AdminUserRecord;
import moe.hhm.shiori.user.admin.repository.AdminUserMapper;
import moe.hhm.shiori.user.domain.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminUserService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AdminUserMapper adminUserMapper;
    private final ObjectMapper objectMapper;

    public AdminUserService(AdminUserMapper adminUserMapper, ObjectMapper objectMapper) {
        this.adminUserMapper = adminUserMapper;
        this.objectMapper = objectMapper;
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
        insertAudit(
                operatorUserId,
                targetUserId,
                "USER_ADMIN_ROLE_UPDATE",
                stateSnapshot(before),
                stateSnapshot(after),
                request.reason()
        );
        return new AdminUserStatusResponse(after.userId(), resolveStatus(after.status()).name(), hasAdminRole(after.roleCodes()));
    }

    public List<AdminRoleResponse> listRoles() {
        return adminUserMapper.listActiveRoles().stream()
                .map(record -> new AdminRoleResponse(record.roleCode(), record.roleName()))
                .toList();
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
                "status", resolveStatus(record.status()).name(),
                "roles", parseRoles(record.roleCodes())
        );
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException e) {
            return "{}";
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
}
