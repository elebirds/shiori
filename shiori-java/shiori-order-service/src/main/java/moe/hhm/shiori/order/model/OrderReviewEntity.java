package moe.hhm.shiori.order.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderReviewEntity {
    private Long id;
    private String orderNo;
    private Long reviewerUserId;
    private Long reviewedUserId;
    private String reviewerRole;
    private Integer communicationStar;
    private Integer timelinessStar;
    private Integer credibilityStar;
    private BigDecimal overallStar;
    private String comment;
    private String imageObjectKeys;
    private String visibilityStatus;
    private String visibilityReason;
    private Long visibilityOperatorUserId;
    private LocalDateTime visibilityUpdatedAt;
    private Integer editCount;
    private LocalDateTime lastEditedAt;

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

    public Long getReviewerUserId() {
        return reviewerUserId;
    }

    public void setReviewerUserId(Long reviewerUserId) {
        this.reviewerUserId = reviewerUserId;
    }

    public Long getReviewedUserId() {
        return reviewedUserId;
    }

    public void setReviewedUserId(Long reviewedUserId) {
        this.reviewedUserId = reviewedUserId;
    }

    public String getReviewerRole() {
        return reviewerRole;
    }

    public void setReviewerRole(String reviewerRole) {
        this.reviewerRole = reviewerRole;
    }

    public Integer getCommunicationStar() {
        return communicationStar;
    }

    public void setCommunicationStar(Integer communicationStar) {
        this.communicationStar = communicationStar;
    }

    public Integer getTimelinessStar() {
        return timelinessStar;
    }

    public void setTimelinessStar(Integer timelinessStar) {
        this.timelinessStar = timelinessStar;
    }

    public Integer getCredibilityStar() {
        return credibilityStar;
    }

    public void setCredibilityStar(Integer credibilityStar) {
        this.credibilityStar = credibilityStar;
    }

    public BigDecimal getOverallStar() {
        return overallStar;
    }

    public void setOverallStar(BigDecimal overallStar) {
        this.overallStar = overallStar;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getImageObjectKeys() {
        return imageObjectKeys;
    }

    public void setImageObjectKeys(String imageObjectKeys) {
        this.imageObjectKeys = imageObjectKeys;
    }

    public String getVisibilityStatus() {
        return visibilityStatus;
    }

    public void setVisibilityStatus(String visibilityStatus) {
        this.visibilityStatus = visibilityStatus;
    }

    public String getVisibilityReason() {
        return visibilityReason;
    }

    public void setVisibilityReason(String visibilityReason) {
        this.visibilityReason = visibilityReason;
    }

    public Long getVisibilityOperatorUserId() {
        return visibilityOperatorUserId;
    }

    public void setVisibilityOperatorUserId(Long visibilityOperatorUserId) {
        this.visibilityOperatorUserId = visibilityOperatorUserId;
    }

    public LocalDateTime getVisibilityUpdatedAt() {
        return visibilityUpdatedAt;
    }

    public void setVisibilityUpdatedAt(LocalDateTime visibilityUpdatedAt) {
        this.visibilityUpdatedAt = visibilityUpdatedAt;
    }

    public Integer getEditCount() {
        return editCount;
    }

    public void setEditCount(Integer editCount) {
        this.editCount = editCount;
    }

    public LocalDateTime getLastEditedAt() {
        return lastEditedAt;
    }

    public void setLastEditedAt(LocalDateTime lastEditedAt) {
        this.lastEditedAt = lastEditedAt;
    }
}
