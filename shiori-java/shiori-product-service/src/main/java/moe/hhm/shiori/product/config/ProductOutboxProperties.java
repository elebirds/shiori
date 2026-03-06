package moe.hhm.shiori.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "product.outbox")
public class ProductOutboxProperties {

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
