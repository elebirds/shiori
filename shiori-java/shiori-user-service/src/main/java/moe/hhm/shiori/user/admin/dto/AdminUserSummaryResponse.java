package moe.hhm.shiori.user.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserSummaryResponse(
        Long userId,
        String userNo,
        String username,
        String nickname,
        String status,
        List<String> roles,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
