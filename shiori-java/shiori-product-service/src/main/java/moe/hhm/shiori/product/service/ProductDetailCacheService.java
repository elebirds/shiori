package moe.hhm.shiori.product.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.config.ProductDetailCacheProperties;
import moe.hhm.shiori.product.dto.SpecItemResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProductDetailCacheService {

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    @Nullable
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductDetailCacheProperties properties;

    public ProductDetailCacheService(@Nullable StringRedisTemplate stringRedisTemplate,
                                     ObjectMapper objectMapper,
                                     ProductDetailCacheProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public CachedProductDetail getOnSaleProductDetail(Long productId, Supplier<CachedProductDetail> loader) {
        if (stringRedisTemplate == null) {
            return loader.get();
        }

        try {
            CacheEnvelope cached = readEnvelope(productId);
            if (cached != null) {
                return unwrapEnvelope(cached);
            }

            String token = UUID.randomUUID().toString();
            boolean locked = tryLock(productId, token);
            if (locked) {
                try {
                    CacheEnvelope doubleChecked = readEnvelope(productId);
                    if (doubleChecked != null) {
                        return unwrapEnvelope(doubleChecked);
                    }
                    return loadAndCache(productId, loader);
                } finally {
                    unlock(productId, token);
                }
            }

            for (int i = 0; i < properties.getLockWaitAttempts(); i++) {
                pause();
                CacheEnvelope waited = readEnvelope(productId);
                if (waited != null) {
                    return unwrapEnvelope(waited);
                }
            }

            return loadAndCache(productId, loader);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return loader.get();
        }
    }

    public void evictProductDetail(Long productId) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(cacheKey(productId));
        } catch (RuntimeException ignored) {
        }
    }

    private CachedProductDetail loadAndCache(Long productId, Supplier<CachedProductDetail> loader) {
        try {
            CachedProductDetail detail = loader.get();
            writeEnvelope(productId, CacheEnvelope.found(detail), positiveTtl());
            return detail;
        } catch (BizException ex) {
            if (ProductErrorCode.PRODUCT_NOT_FOUND.equals(ex.getErrorCode())) {
                writeEnvelope(productId, CacheEnvelope.notFound(), Duration.ofSeconds(properties.getNullTtlSeconds()));
            } else if (ProductErrorCode.PRODUCT_NOT_ON_SALE.equals(ex.getErrorCode())) {
                writeEnvelope(productId, CacheEnvelope.notOnSale(), Duration.ofSeconds(properties.getNullTtlSeconds()));
            }
            throw ex;
        }
    }

    @Nullable
    private CacheEnvelope readEnvelope(Long productId) {
        String payload = stringRedisTemplate.opsForValue().get(cacheKey(productId));
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, CacheEnvelope.class);
        } catch (JacksonException ex) {
            stringRedisTemplate.delete(cacheKey(productId));
            return null;
        }
    }

    private CachedProductDetail unwrapEnvelope(CacheEnvelope envelope) {
        if (envelope.state() == CacheState.FOUND && envelope.detail() != null) {
            return envelope.detail();
        }
        if (envelope.state() == CacheState.PRODUCT_NOT_FOUND) {
            throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        throw new BizException(ProductErrorCode.PRODUCT_NOT_ON_SALE, HttpStatus.BAD_REQUEST);
    }

    private boolean tryLock(Long productId, String token) {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey(productId),
                token,
                Duration.ofMillis(properties.getLockTtlMillis())
        );
        return Boolean.TRUE.equals(locked);
    }

    private void unlock(Long productId, String token) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey(productId)), token);
    }

    private void writeEnvelope(Long productId, CacheEnvelope envelope, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey(productId),
                    objectMapper.writeValueAsString(envelope),
                    ttl
            );
        } catch (JacksonException ignored) {
        }
    }

    private Duration positiveTtl() {
        int jitterSeconds = properties.getTtlJitterSeconds() <= 0
                ? 0
                : ThreadLocalRandom.current().nextInt(properties.getTtlJitterSeconds() + 1);
        return Duration.ofSeconds(properties.getTtlSeconds() + jitterSeconds);
    }

    private void pause() {
        if (properties.getLockWaitMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.getLockWaitMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String cacheKey(Long productId) {
        return properties.getKeyPrefix() + productId;
    }

    private String lockKey(Long productId) {
        return cacheKey(productId) + ":lock";
    }

    public enum CacheState {
        FOUND,
        PRODUCT_NOT_FOUND,
        PRODUCT_NOT_ON_SALE
    }

    public record CacheEnvelope(CacheState state, CachedProductDetail detail) {

        public static CacheEnvelope found(CachedProductDetail detail) {
            return new CacheEnvelope(CacheState.FOUND, detail);
        }

        public static CacheEnvelope notFound() {
            return new CacheEnvelope(CacheState.PRODUCT_NOT_FOUND, null);
        }

        public static CacheEnvelope notOnSale() {
            return new CacheEnvelope(CacheState.PRODUCT_NOT_ON_SALE, null);
        }
    }

    public record CachedProductDetail(
            Long productId,
            String productNo,
            Long ownerUserId,
            String title,
            String description,
            String detailHtml,
            String coverObjectKey,
            String categoryCode,
            String subCategoryCode,
            String conditionLevel,
            String tradeMode,
            String campusCode,
            Long minPriceCent,
            Long maxPriceCent,
            Integer totalStock,
            List<CachedSku> skus
    ) {
    }

    public record CachedSku(
            Long skuId,
            String skuNo,
            String displayName,
            List<SpecItemResponse> specItems,
            Long priceCent,
            Integer stock
    ) {
    }
}
