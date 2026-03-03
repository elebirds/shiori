package moe.hhm.shiori.user.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserDetailResponse(
        Long userId,
        String userNo,
        String username,
        String nickname,
        String avatarUrl,
        String status,
        Integer failedLoginCount,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt,
        String lastLoginIp,
        List<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
