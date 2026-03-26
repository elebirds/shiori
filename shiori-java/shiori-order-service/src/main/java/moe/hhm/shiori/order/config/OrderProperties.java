package moe.hhm.shiori.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "order")
public class OrderProperties {

    private long timeoutMinutes = 15;
    private final Command command = new Command();
    private final Review review = new Review();
    private final Refund refund = new Refund();
    private final TimeoutScheduler timeoutScheduler = new TimeoutScheduler();

    public long getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(long timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public Command getCommand() {
        return command;
    }

    public Review getReview() {
        return review;
    }

    public Refund getRefund() {
        return refund;
    }

    public TimeoutScheduler getTimeoutScheduler() {
        return timeoutScheduler;
    }

    public static class Command {
        private boolean enabled = true;
        private long recoveryFixedDelayMs = 3000;
        private int recoveryBatchSize = 100;
        private int stalePreparedSeconds = 30;
        private int maxBackoffSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getRecoveryFixedDelayMs() {
            return recoveryFixedDelayMs;
        }

        public void setRecoveryFixedDelayMs(long recoveryFixedDelayMs) {
            this.recoveryFixedDelayMs = recoveryFixedDelayMs;
        }

        public int getRecoveryBatchSize() {
            return recoveryBatchSize;
        }

        public void setRecoveryBatchSize(int recoveryBatchSize) {
            this.recoveryBatchSize = recoveryBatchSize;
        }

        public int getStalePreparedSeconds() {
            return stalePreparedSeconds;
        }

        public void setStalePreparedSeconds(int stalePreparedSeconds) {
            this.stalePreparedSeconds = stalePreparedSeconds;
        }

        public int getMaxBackoffSeconds() {
            return maxBackoffSeconds;
        }

        public void setMaxBackoffSeconds(int maxBackoffSeconds) {
            this.maxBackoffSeconds = maxBackoffSeconds;
        }
    }

    public static class Review {
        private int windowDays = 15;
        private int editHours = 24;
        private int commentMaxLength = 280;

        public int getWindowDays() {
            return windowDays;
        }

        public void setWindowDays(int windowDays) {
            this.windowDays = windowDays;
        }

        public int getEditHours() {
            return editHours;
        }

        public void setEditHours(int editHours) {
            this.editHours = editHours;
        }

        public int getCommentMaxLength() {
            return commentMaxLength;
        }

        public void setCommentMaxLength(int commentMaxLength) {
            this.commentMaxLength = commentMaxLength;
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

    public static class TimeoutScheduler {
        private boolean enabled = true;
        private long fixedDelayMs = 3000;
        private int batchSize = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
