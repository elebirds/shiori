package moe.hhm.shiori.user.admin.model;

import java.time.LocalDateTime;

public record AdminUserRecord(
        Long userId,
        String userNo,
        String username,
        String nickname,
        String avatarUrl,
        Integer status,
        Integer failedLoginCount,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt,
        String lastLoginIp,
        String roleCodes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer isDeleted
) {
}
