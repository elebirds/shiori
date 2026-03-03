package moe.hhm.shiori.order.dto;

import java.util.List;

public record OrderStatusAuditPageResponse(
        long total,
        int page,
        int size,
        List<OrderStatusAuditItemResponse> items
) {
}
