package moe.hhm.shiori.order.dto;

import java.time.LocalDateTime;

public record OrderRefundResponse(
        String refundNo,
        String orderNo,
        String status,
        Long amountCent,
        String applyReason,
        String rejectReason,
        Long reviewedByUserId,
        LocalDateTime reviewDeadlineAt,
        LocalDateTime reviewedAt,
        boolean autoApproved,
        String paymentNo,
        String lastError,
        Integer retryCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean idempotent
) {
}
