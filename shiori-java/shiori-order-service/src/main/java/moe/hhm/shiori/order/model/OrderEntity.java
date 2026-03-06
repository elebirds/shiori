package moe.hhm.shiori.order.model;

import java.time.LocalDateTime;

public class OrderEntity {
    private Long id;
    private String orderNo;
    private Long buyerUserId;
    private Long sellerUserId;
    private Integer status;
    private Long totalAmountCent;
    private Integer itemCount;
    private String paymentNo;
    private String refundStatus;
    private String refundNo;
    private Long refundAmountCent;
    private LocalDateTime refundUpdatedAt;
    private String cancelReason;
    private LocalDateTime timeoutAt;
    private LocalDateTime paidAt;
    private LocalDateTime finishedAt;
    private String bizSource;
    private Long chatConversationId;
    private Long chatListingId;
    private Integer allowMeetup;
    private Integer allowDelivery;
    private String fulfillmentMode;
    private Long shippingAddressId;
    private String shippingReceiverName;
    private String shippingReceiverPhone;
    private String shippingProvince;
    private String shippingCity;
    private String shippingDistrict;
    private String shippingDetailAddress;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getBuyerUserId() {
        return buyerUserId;
    }

    public void setBuyerUserId(Long buyerUserId) {
        this.buyerUserId = buyerUserId;
    }

    public Long getSellerUserId() {
        return sellerUserId;
    }

    public void setSellerUserId(Long sellerUserId) {
        this.sellerUserId = sellerUserId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getTotalAmountCent() {
        return totalAmountCent;
    }

    public void setTotalAmountCent(Long totalAmountCent) {
        this.totalAmountCent = totalAmountCent;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public Long getRefundAmountCent() {
        return refundAmountCent;
    }

    public void setRefundAmountCent(Long refundAmountCent) {
        this.refundAmountCent = refundAmountCent;
    }

    public LocalDateTime getRefundUpdatedAt() {
        return refundUpdatedAt;
    }

    public void setRefundUpdatedAt(LocalDateTime refundUpdatedAt) {
        this.refundUpdatedAt = refundUpdatedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public LocalDateTime getTimeoutAt() {
        return timeoutAt;
    }

    public void setTimeoutAt(LocalDateTime timeoutAt) {
        this.timeoutAt = timeoutAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getBizSource() {
        return bizSource;
    }

    public void setBizSource(String bizSource) {
        this.bizSource = bizSource;
    }

    public Long getChatConversationId() {
        return chatConversationId;
    }

    public void setChatConversationId(Long chatConversationId) {
        this.chatConversationId = chatConversationId;
    }

    public Long getChatListingId() {
        return chatListingId;
    }

    public void setChatListingId(Long chatListingId) {
        this.chatListingId = chatListingId;
    }

    public Integer getAllowMeetup() {
        return allowMeetup;
    }

    public void setAllowMeetup(Integer allowMeetup) {
        this.allowMeetup = allowMeetup;
    }

    public Integer getAllowDelivery() {
        return allowDelivery;
    }

    public void setAllowDelivery(Integer allowDelivery) {
        this.allowDelivery = allowDelivery;
    }

    public String getFulfillmentMode() {
        return fulfillmentMode;
    }

    public void setFulfillmentMode(String fulfillmentMode) {
        this.fulfillmentMode = fulfillmentMode;
    }

    public Long getShippingAddressId() {
        return shippingAddressId;
    }

    public void setShippingAddressId(Long shippingAddressId) {
        this.shippingAddressId = shippingAddressId;
    }

    public String getShippingReceiverName() {
        return shippingReceiverName;
    }

    public void setShippingReceiverName(String shippingReceiverName) {
        this.shippingReceiverName = shippingReceiverName;
    }

    public String getShippingReceiverPhone() {
        return shippingReceiverPhone;
    }

    public void setShippingReceiverPhone(String shippingReceiverPhone) {
        this.shippingReceiverPhone = shippingReceiverPhone;
    }

    public String getShippingProvince() {
        return shippingProvince;
    }

    public void setShippingProvince(String shippingProvince) {
        this.shippingProvince = shippingProvince;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public void setShippingCity(String shippingCity) {
        this.shippingCity = shippingCity;
    }

    public String getShippingDistrict() {
        return shippingDistrict;
    }

    public void setShippingDistrict(String shippingDistrict) {
        this.shippingDistrict = shippingDistrict;
    }

    public String getShippingDetailAddress() {
        return shippingDetailAddress;
    }

    public void setShippingDetailAddress(String shippingDetailAddress) {
        this.shippingDetailAddress = shippingDetailAddress;
    }
}
