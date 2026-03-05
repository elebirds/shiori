package moe.hhm.shiori.order.dto.v2;

import java.util.List;

public record PraiseWallPageResponse(
        long total,
        int page,
        int size,
        List<PraiseWallItemResponse> items
) {
}

