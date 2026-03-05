package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;

public record OrderReviewAccessState(
        boolean myReviewSubmitted,
        boolean counterpartyReviewSubmitted,
        boolean canCreateReview,
        boolean canEditReview,
        LocalDateTime reviewDeadlineAt
) {
    public static OrderReviewAccessState empty() {
        return new OrderReviewAccessState(false, false, false, false, null);
    }
}

