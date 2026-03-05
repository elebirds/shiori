package moe.hhm.shiori.user.authz.dto;

import java.time.LocalDateTime;

public record AdminUserPermissionOverrideResponse(
        Long id,
        Long userId,
        String permissionCode,
        String effect,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String reason,
        Long operatorUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
