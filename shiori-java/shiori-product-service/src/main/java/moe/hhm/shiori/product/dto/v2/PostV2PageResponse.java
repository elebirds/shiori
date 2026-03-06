package moe.hhm.shiori.product.dto.v2;

import java.util.List;

public record PostV2PageResponse(
        long total,
        int page,
        int size,
        List<PostV2ItemResponse> items
) {
}
