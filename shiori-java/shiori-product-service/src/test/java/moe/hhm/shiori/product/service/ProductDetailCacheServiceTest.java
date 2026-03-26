package moe.hhm.shiori.product.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.config.ProductDetailCacheProperties;
import moe.hhm.shiori.product.dto.SpecItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDetailCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ProductDetailCacheService productDetailCacheService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ProductDetailCacheProperties properties = new ProductDetailCacheProperties();
        properties.setTtlSeconds(120);
        properties.setNullTtlSeconds(15);
        properties.setTtlJitterSeconds(30);
        properties.setLockTtlMillis(3000);
        properties.setLockWaitMillis(0);
        properties.setLockWaitAttempts(2);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.execute(any(), anyList(), anyString())).thenReturn(1L);
        productDetailCacheService = new ProductDetailCacheService(stringRedisTemplate, objectMapper, properties);
    }

    @Test
    void shouldReturnCachedSnapshotWithoutCallingLoader() throws Exception {
        ProductDetailCacheService.CachedProductDetail snapshot = cachedProductDetail();
        when(valueOperations.get("product:detail:v2:1")).thenReturn(
                objectMapper.writeValueAsString(ProductDetailCacheService.CacheEnvelope.found(snapshot))
        );

        ProductDetailCacheService.CachedProductDetail loaded =
                productDetailCacheService.getOnSaleProductDetail(1L, failLoader());

        assertThat(loaded.productId()).isEqualTo(1L);
        assertThat(loaded.skus()).hasSize(1);
    }

    @Test
    void shouldCacheProductNotFoundAsNegativeMarker() {
        when(valueOperations.get("product:detail:v2:9")).thenReturn(null);
        when(valueOperations.setIfAbsent(eq("product:detail:v2:9:lock"), anyString(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> productDetailCacheService.getOnSaleProductDetail(9L, () -> {
            throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND);
        })).isInstanceOf(BizException.class);

        verify(valueOperations).set(eq("product:detail:v2:9"), anyString(), any());
    }

    @Test
    void shouldWaitForPeerToWarmCacheBeforeFallingBackToLoader() throws Exception {
        ProductDetailCacheService.CachedProductDetail snapshot = cachedProductDetail();
        when(valueOperations.get("product:detail:v2:7"))
                .thenReturn(null)
                .thenReturn(objectMapper.writeValueAsString(ProductDetailCacheService.CacheEnvelope.found(snapshot)));
        when(valueOperations.setIfAbsent(eq("product:detail:v2:7:lock"), anyString(), any()))
                .thenReturn(false);

        ProductDetailCacheService.CachedProductDetail loaded =
                productDetailCacheService.getOnSaleProductDetail(7L, failLoader());

        assertThat(loaded.productNo()).isEqualTo("P001");
    }

    @Test
    void shouldLoadFromDatabaseAndPopulateCacheWhenMissed() {
        AtomicInteger loaderCalls = new AtomicInteger();
        when(valueOperations.get("product:detail:v2:5")).thenReturn(null);
        when(valueOperations.setIfAbsent(eq("product:detail:v2:5:lock"), anyString(), any()))
                .thenReturn(true);

        ProductDetailCacheService.CachedProductDetail loaded =
                productDetailCacheService.getOnSaleProductDetail(5L, () -> {
                    loaderCalls.incrementAndGet();
                    return cachedProductDetail();
                });

        assertThat(loaderCalls.get()).isEqualTo(1);
        assertThat(loaded.productId()).isEqualTo(1L);
        verify(valueOperations).set(eq("product:detail:v2:5"), anyString(), any());
    }

    @Test
    void shouldEvictDetailCacheByProductId() {
        productDetailCacheService.evictProductDetail(11L);

        verify(stringRedisTemplate).delete("product:detail:v2:11");
    }

    private Supplier<ProductDetailCacheService.CachedProductDetail> failLoader() {
        return () -> {
            throw new AssertionError("loader should not be called");
        };
    }

    private ProductDetailCacheService.CachedProductDetail cachedProductDetail() {
        return new ProductDetailCacheService.CachedProductDetail(
                1L,
                "P001",
                1001L,
                "Java Book",
                "summary",
                "<p>detail</p>",
                "product/1001/202603/cover.jpg",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main_campus",
                3900L,
                3900L,
                8,
                List.of(new ProductDetailCacheService.CachedSku(
                        10L,
                        "SKU-10",
                        "标准版",
                        List.of(new SpecItemResponse("版本", "标准版")),
                        3900L,
                        8
                ))
        );
    }
}
