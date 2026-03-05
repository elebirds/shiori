package moe.hhm.shiori.user.follow.dto;

import java.time.LocalDateTime;

public record FollowUserItemResponse(
        Long userId,
        String userNo,
        String nickname,
        String avatarUrl,
        String bio,
        LocalDateTime followedAt
) {
}
