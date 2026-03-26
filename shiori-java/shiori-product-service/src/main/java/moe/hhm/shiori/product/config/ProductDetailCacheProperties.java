package moe.hhm.shiori.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product.detail-cache")
public class ProductDetailCacheProperties {

    private String keyPrefix = "product:detail:v2:";
    private long ttlSeconds = 120;
    private long nullTtlSeconds = 15;
    private int ttlJitterSeconds = 30;
    private long lockTtlMillis = 3000;
    private long lockWaitMillis = 40;
    private int lockWaitAttempts = 5;

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

    public long getNullTtlSeconds() {
        return nullTtlSeconds;
    }

    public void setNullTtlSeconds(long nullTtlSeconds) {
        this.nullTtlSeconds = nullTtlSeconds;
    }

    public int getTtlJitterSeconds() {
        return ttlJitterSeconds;
    }

    public void setTtlJitterSeconds(int ttlJitterSeconds) {
        this.ttlJitterSeconds = ttlJitterSeconds;
    }

    public long getLockTtlMillis() {
        return lockTtlMillis;
    }

    public void setLockTtlMillis(long lockTtlMillis) {
        this.lockTtlMillis = lockTtlMillis;
    }

    public long getLockWaitMillis() {
        return lockWaitMillis;
    }

    public void setLockWaitMillis(long lockWaitMillis) {
        this.lockWaitMillis = lockWaitMillis;
    }

    public int getLockWaitAttempts() {
        return lockWaitAttempts;
    }

    public void setLockWaitAttempts(int lockWaitAttempts) {
        this.lockWaitAttempts = lockWaitAttempts;
    }
}
