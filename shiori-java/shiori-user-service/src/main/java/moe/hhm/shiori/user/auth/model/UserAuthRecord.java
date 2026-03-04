package moe.hhm.shiori.user.auth.model;

import java.time.LocalDateTime;

public record UserAuthRecord(
        Long id,
        String userNo,
        String username,
        String passwordHash,
        Integer status,
        Integer failedLoginCount,
        LocalDateTime lockedUntil,
        Integer mustChangePassword,
        Integer isDeleted
) {
}
