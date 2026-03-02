package moe.hhm.shiori.product.dto;

import java.util.List;

public record ProductPageResponse(
        long total,
        int page,
        int size,
        List<ProductSummaryResponse> items
) {
}
