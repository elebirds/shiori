package moe.hhm.shiori.order.dto.v2;

import java.util.List;

public record AdminOrderReviewPageResponse(
        long total,
        int page,
        int size,
        List<OrderReviewItemResponse> items
) {
}

