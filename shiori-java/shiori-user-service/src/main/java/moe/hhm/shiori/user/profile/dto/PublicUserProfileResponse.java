package moe.hhm.shiori.user.profile.dto;

import java.time.LocalDateTime;

public record PublicUserProfileResponse(
        Long userId,
        String userNo,
        String username,
        String nickname,
        String avatarUrl,
        Integer gender,
        Integer age,
        String bio,
        Long followerCount,
        Long followingCount,
        Boolean followedByCurrentUser,
        LocalDateTime lastActiveAt
) {
}
