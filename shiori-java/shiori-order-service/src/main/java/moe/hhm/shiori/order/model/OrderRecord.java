package moe.hhm.shiori.order.model;

import java.time.LocalDateTime;

public record OrderRecord(
        Long id,
        String orderNo,
        Long buyerUserId,
        Long sellerUserId,
        Integer status,
        Long totalAmountCent,
        Integer itemCount,
        String paymentNo,
        String refundStatus,
        String refundNo,
        Long refundAmountCent,
        LocalDateTime refundUpdatedAt,
        String cancelReason,
        LocalDateTime timeoutAt,
        LocalDateTime paidAt,
        LocalDateTime finishedAt,
        String bizSource,
        Long chatConversationId,
        Long chatListingId,
        Integer isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
