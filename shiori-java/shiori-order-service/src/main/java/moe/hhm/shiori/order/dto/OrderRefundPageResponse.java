package moe.hhm.shiori.order.dto;

import java.util.List;

public record OrderRefundPageResponse(
        long total,
        int page,
        int size,
        List<OrderRefundResponse> items
) {
}
