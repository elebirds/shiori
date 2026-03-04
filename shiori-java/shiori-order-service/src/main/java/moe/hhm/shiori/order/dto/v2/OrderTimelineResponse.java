package moe.hhm.shiori.order.dto.v2;

import java.util.List;

public record OrderTimelineResponse(
        long total,
        int page,
        int size,
        List<OrderTimelineItemResponse> items
) {
}
