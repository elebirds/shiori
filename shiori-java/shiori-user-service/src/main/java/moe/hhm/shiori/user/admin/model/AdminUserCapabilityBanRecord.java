package moe.hhm.shiori.user.admin.model;

import java.time.LocalDateTime;

public record AdminUserCapabilityBanRecord(
        Long id,
        Long userId,
        String capabilityCode,
        Integer isBanned,
        String reason,
        Long operatorUserId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
