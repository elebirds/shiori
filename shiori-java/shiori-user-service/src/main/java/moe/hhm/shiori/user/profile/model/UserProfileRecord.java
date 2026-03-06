package moe.hhm.shiori.user.profile.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserProfileRecord(
        Long userId,
        String userNo,
        String username,
        String nickname,
        Integer gender,
        LocalDate birthDate,
        String bio,
        String avatarUrl,
        LocalDateTime lastLoginAt
) {
}
