package moe.hhm.shiori.social.dto.v2;

import java.util.List;

public record PostV2PageResponse(
        long total,
        int page,
        int size,
        List<PostV2ItemResponse> items
) {
}
