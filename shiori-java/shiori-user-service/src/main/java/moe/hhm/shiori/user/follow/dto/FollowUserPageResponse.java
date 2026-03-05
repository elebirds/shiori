package moe.hhm.shiori.user.follow.dto;

import java.util.List;

public record FollowUserPageResponse(
        long total,
        int page,
        int size,
        List<FollowUserItemResponse> items
) {
}
