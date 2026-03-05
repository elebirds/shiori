package moe.hhm.shiori.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "order")
public class OrderProperties {

    private long timeoutMinutes = 15;
    private final Outbox outbox = new Outbox();
    private final Review review = new Review();

    public long getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(long timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Review getReview() {
        return review;
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
}
