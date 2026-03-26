package moe.hhm.shiori.product.service;

import java.time.Duration;
import java.util.Map;
import moe.hhm.shiori.common.storage.OssObjectService;
import moe.hhm.shiori.common.storage.OssProperties;
import moe.hhm.shiori.product.config.ProductMediaUrlCacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

public class ProductCachedOssObjectService implements OssObjectService {

    private final OssObjectService delegate;
    @Nullable
    private final StringRedisTemplate stringRedisTemplate;
    private final ProductMediaUrlCacheProperties properties;
    private final OssProperties ossProperties;

    public ProductCachedOssObjectService(OssObjectService delegate,
                                         @Nullable StringRedisTemplate stringRedisTemplate,
                                         ProductMediaUrlCacheProperties properties,
                                         OssProperties ossProperties) {
        this.delegate = delegate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.ossProperties = ossProperties;
    }

    @Override
    public PresignUploadResult presignUpload(Long ownerUserId, String fileName, String contentType) {
        return delegate.presignUpload(ownerUserId, fileName, contentType);
    }

    @Override
    public String presignGetUrl(String objectKey) {
        if (!properties.isEnabled() || stringRedisTemplate == null || !StringUtils.hasText(objectKey)) {
            return delegate.presignGetUrl(objectKey);
        }

        String cacheKey = properties.getKeyPrefix() + objectKey;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
        } catch (RuntimeException ex) {
            return delegate.presignGetUrl(objectKey);
        }

        String signedUrl = delegate.presignGetUrl(objectKey);
        if (!StringUtils.hasText(signedUrl)) {
            return signedUrl;
        }

        try {
            stringRedisTemplate.opsForValue().set(cacheKey, signedUrl, cacheTtl());
        } catch (RuntimeException ignored) {
        }
        return signedUrl;
    }

    private Duration cacheTtl() {
        long configuredTtl = Math.max(1L, properties.getTtlSeconds());
        long maxSafeTtl = Math.max(1L, ossProperties.getPresignGetExpireSeconds() - Math.max(0L, properties.getSafetyMarginSeconds()));
        return Duration.ofSeconds(Math.min(configuredTtl, maxSafeTtl));
    }
}
