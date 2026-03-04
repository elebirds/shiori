package moe.hhm.shiori.user.admin.dto;

import java.time.LocalDateTime;

public record AdminUserAuditItemResponse(
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
