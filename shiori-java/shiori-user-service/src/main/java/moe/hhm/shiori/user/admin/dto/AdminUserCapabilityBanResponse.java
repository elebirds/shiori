package moe.hhm.shiori.user.admin.dto;

import java.time.LocalDateTime;

public record AdminUserCapabilityBanResponse(
        Long id,
        Long userId,
        String capability,
        boolean banned,
        String reason,
        Long operatorUserId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
