package moe.hhm.shiori.user.profile.model;

public record UserProfileRecord(
        Long userId,
        String userNo,
        String username,
        String nickname,
        String avatarUrl
) {
}
