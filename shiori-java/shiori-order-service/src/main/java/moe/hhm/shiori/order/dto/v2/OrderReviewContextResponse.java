package moe.hhm.shiori.order.dto.v2;

import java.time.LocalDateTime;

public record OrderReviewContextResponse(
        String orderNo,
        boolean myReviewSubmitted,
        boolean counterpartyReviewSubmitted,
        boolean canCreateReview,
        boolean canEditReview,
        LocalDateTime reviewDeadlineAt,
        OrderReviewItemResponse myReview,
        OrderReviewItemResponse counterpartyReview
) {
}

