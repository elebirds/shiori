package moe.hhm.shiori.order.model;

import java.time.LocalDateTime;

public record OrderRefundRecord(
        Long id,
        String refundNo,
        String orderNo,
        Long buyerUserId,
        Long sellerUserId,
        Long amountCent,
        String status,
        String applyReason,
        String rejectReason,
        Long reviewedByUserId,
        LocalDateTime reviewDeadlineAt,
        LocalDateTime reviewedAt,
        Integer autoApproved,
        String paymentNo,
        String lastError,
        Integer retryCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
