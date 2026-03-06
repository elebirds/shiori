package moe.hhm.shiori.order.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        String orderNo,
        Long buyerUserId,
        Long sellerUserId,
        String status,
        Long totalAmountCent,
        String source,
        Long conversationId,
        Long listingId,
        boolean allowMeetup,
        boolean allowDelivery,
        String fulfillmentMode,
        OrderShippingAddressResponse shippingAddress,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime finishedAt,
        LocalDateTime timeoutAt,
        String refundStatus,
        String refundNo,
        Long refundAmountCent,
        LocalDateTime refundUpdatedAt,
        List<OrderItemResponse> items,
        boolean myReviewSubmitted,
        boolean counterpartyReviewSubmitted,
        boolean canCreateReview,
        boolean canEditReview,
        LocalDateTime reviewDeadlineAt
) {
}
