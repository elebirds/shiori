package moe.hhm.shiori.user.authz.model;

import java.time.LocalDateTime;

public record UserPermissionOverrideRecord(
        Long id,
        Long userId,
        String permissionCode,
        String effect,
        String reason,
        Long operatorUserId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
