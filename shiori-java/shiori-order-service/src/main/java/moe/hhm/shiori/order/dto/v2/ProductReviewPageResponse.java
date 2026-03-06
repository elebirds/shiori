package moe.hhm.shiori.order.dto.v2;

import java.util.List;

public record ProductReviewPageResponse(
        long total,
        int page,
        int size,
        List<ProductReviewItemResponse> items
) {
}
