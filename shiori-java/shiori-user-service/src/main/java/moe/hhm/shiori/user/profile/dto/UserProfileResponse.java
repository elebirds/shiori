package moe.hhm.shiori.user.profile.dto;

import java.time.LocalDate;

public record UserProfileResponse(
        Long userId,
        String userNo,
        String username,
        String nickname,
        Integer gender,
        LocalDate birthDate,
        Integer age,
        String bio,
        String avatarUrl
) {
}
