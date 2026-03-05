package moe.hhm.shiori.user.follow.model;

import java.time.LocalDateTime;

public record FollowUserRecord(
        Long userId,
        String userNo,
        String nickname,
        String avatarUrl,
        String bio,
        LocalDateTime followedAt
) {
}
