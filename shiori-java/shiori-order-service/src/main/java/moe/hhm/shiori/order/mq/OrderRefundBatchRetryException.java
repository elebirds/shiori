package moe.hhm.shiori.order.mq;

public class OrderRefundBatchRetryException extends RuntimeException {

    private final Long sellerUserId;
    private final int failedCount;

    public OrderRefundBatchRetryException(Long sellerUserId, int failedCount) {
        super("Kafka 事件触发退款重试仍有失败, sellerUserId=" + sellerUserId + ", failedCount=" + failedCount);
        this.sellerUserId = sellerUserId;
        this.failedCount = failedCount;
    }

    public Long getSellerUserId() {
        return sellerUserId;
    }

    public int getFailedCount() {
        return failedCount;
    }
}
