package moe.hhm.shiori.user.admin.model;

import java.time.LocalDateTime;

public record AdminUserAuditRecord(
        Long id,
        Long operatorUserId,
        Long targetUserId,
        String action,
        String beforeJson,
        String afterJson,
        String reason,
        LocalDateTime createdAt
) {
}
