package moe.hhm.shiori.order.dto;

import java.util.List;

public record OrderPageResponse(
        long total,
        int page,
        int size,
        List<OrderSummaryResponse> items
) {
}
