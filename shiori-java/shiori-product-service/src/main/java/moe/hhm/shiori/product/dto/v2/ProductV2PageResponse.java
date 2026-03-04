package moe.hhm.shiori.product.dto.v2;

import java.util.List;

public record ProductV2PageResponse(
        long total,
        int page,
        int size,
        List<ProductV2SummaryResponse> items
) {
}
