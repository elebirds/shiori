package moe.hhm.shiori.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "order")
public class OrderProperties {

    private long timeoutMinutes = 15;
    private final Outbox outbox = new Outbox();
    private final Refund refund = new Refund();

    public long getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(long timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Refund getRefund() {
        return refund;
    }

    public static class Outbox {
        private boolean enabled = true;
        private long relayFixedDelayMs = 3000;
        private int relayBatchSize = 100;
        private int maxBackoffSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getRelayFixedDelayMs() {
            return relayFixedDelayMs;
        }

        public void setRelayFixedDelayMs(long relayFixedDelayMs) {
            this.relayFixedDelayMs = relayFixedDelayMs;
        }

        public int getRelayBatchSize() {
            return relayBatchSize;
        }

        public void setRelayBatchSize(int relayBatchSize) {
            this.relayBatchSize = relayBatchSize;
        }

        public int getMaxBackoffSeconds() {
            return maxBackoffSeconds;
        }

        public void setMaxBackoffSeconds(int maxBackoffSeconds) {
            this.maxBackoffSeconds = maxBackoffSeconds;
        }
    }

    public static class Refund {
        private long reviewSlaHours = 48;
        private long autoApproveFixedDelayMs = 60000;
        private int autoApproveBatchSize = 100;
        private int retryBatchSize = 100;

        public long getReviewSlaHours() {
            return reviewSlaHours;
        }

        public void setReviewSlaHours(long reviewSlaHours) {
            this.reviewSlaHours = reviewSlaHours;
        }

        public long getAutoApproveFixedDelayMs() {
            return autoApproveFixedDelayMs;
        }

        public void setAutoApproveFixedDelayMs(long autoApproveFixedDelayMs) {
            this.autoApproveFixedDelayMs = autoApproveFixedDelayMs;
        }

        public int getAutoApproveBatchSize() {
            return autoApproveBatchSize;
        }

        public void setAutoApproveBatchSize(int autoApproveBatchSize) {
            this.autoApproveBatchSize = autoApproveBatchSize;
        }

        public int getRetryBatchSize() {
            return retryBatchSize;
        }

        public void setRetryBatchSize(int retryBatchSize) {
            this.retryBatchSize = retryBatchSize;
        }
    }
}
