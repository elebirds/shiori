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
        String cancelReason,
        LocalDateTime timeoutAt,
        LocalDateTime paidAt,
        Integer isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
