package moe.hhm.shiori.user.profile.dto;

public record UserProfileResponse(
        Long userId,
        String userNo,
        String username,
        String nickname,
        String avatarUrl
) {
}
