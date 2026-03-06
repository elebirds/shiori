package moe.hhm.shiori.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private final Outbox outbox = new Outbox();
    private final Reconcile reconcile = new Reconcile();

    public Outbox getOutbox() {
        return outbox;
    }

    public Reconcile getReconcile() {
        return reconcile;
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

    public static class Reconcile {
        private boolean enabled = true;
        private String cron = "0 30 2 * * *";
        private String zone = "Asia/Shanghai";
        private int batchSize = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
