package moe.hhm.shiori.order.dto.v2;

import java.util.List;

public record SellerOrderPageResponse(
        long total,
        int page,
        int size,
        List<SellerOrderSummaryResponse> items
) {
}
