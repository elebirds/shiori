package moe.hhm.shiori.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product.media-url-cache")
public class ProductMediaUrlCacheProperties {

    private boolean enabled = true;
    private String keyPrefix = "product:media:url:";
    private long ttlSeconds = 180;
    private long safetyMarginSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public long getSafetyMarginSeconds() {
        return safetyMarginSeconds;
    }

    public void setSafetyMarginSeconds(long safetyMarginSeconds) {
        this.safetyMarginSeconds = safetyMarginSeconds;
    }
}
