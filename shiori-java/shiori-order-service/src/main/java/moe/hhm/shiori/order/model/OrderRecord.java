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
        Integer allowMeetup,
        Integer allowDelivery,
        String fulfillmentMode,
        Long shippingAddressId,
        String shippingReceiverName,
        String shippingReceiverPhone,
        String shippingProvince,
        String shippingCity,
        String shippingDistrict,
        String shippingDetailAddress,
        Integer isDeleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
