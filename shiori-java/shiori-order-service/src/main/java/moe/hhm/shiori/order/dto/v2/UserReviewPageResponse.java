package moe.hhm.shiori.order.dto.v2;

import java.util.List;

public record UserReviewPageResponse(
        long total,
        int page,
        int size,
        List<UserReviewItemResponse> items
) {
}
