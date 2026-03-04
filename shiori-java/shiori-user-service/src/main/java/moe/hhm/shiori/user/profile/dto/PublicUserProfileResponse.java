package moe.hhm.shiori.user.profile.dto;

public record PublicUserProfileResponse(
        Long userId,
        String userNo,
        String username,
        String nickname,
        String avatarUrl,
        Integer gender,
        Integer age,
        String bio
) {
}
