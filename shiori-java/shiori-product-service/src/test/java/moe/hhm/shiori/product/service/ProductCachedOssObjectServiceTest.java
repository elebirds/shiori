package moe.hhm.shiori.product.service;

import java.time.Duration;
import java.util.Map;
import moe.hhm.shiori.common.storage.OssObjectService;
import moe.hhm.shiori.common.storage.OssProperties;
import moe.hhm.shiori.product.config.ProductMediaUrlCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCachedOssObjectServiceTest {

    @Mock
    private OssObjectService delegate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ProductCachedOssObjectService cachedOssObjectService;

    @BeforeEach
    void setUp() {
        ProductMediaUrlCacheProperties properties = new ProductMediaUrlCacheProperties();
        properties.setKeyPrefix("product:media:url:");
        properties.setTtlSeconds(240);
        properties.setSafetyMarginSeconds(30);

        OssProperties ossProperties = new OssProperties();
        ossProperties.setPresignGetExpireSeconds(300);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        cachedOssObjectService = new ProductCachedOssObjectService(delegate, stringRedisTemplate, properties, ossProperties);
    }

    @Test
    void shouldReturnCachedSignedUrlWithoutDelegating() {
        when(valueOperations.get("product:media:url:product/1001/a.jpg")).thenReturn("http://cached/url");

        String url = cachedOssObjectService.presignGetUrl("product/1001/a.jpg");

        assertThat(url).isEqualTo("http://cached/url");
        verify(delegate, never()).presignGetUrl("product/1001/a.jpg");
    }

    @Test
    void shouldDelegateAndCacheSignedUrlWithShorterTtl() {
        when(valueOperations.get("product:media:url:product/1001/a.jpg")).thenReturn(null);
        when(delegate.presignGetUrl("product/1001/a.jpg")).thenReturn("http://signed/url");

        String url = cachedOssObjectService.presignGetUrl("product/1001/a.jpg");

        assertThat(url).isEqualTo("http://signed/url");
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("product:media:url:product/1001/a.jpg"), eq("http://signed/url"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(240));
    }

    @Test
    void shouldCapCacheTtlBelowPresignExpiry() {
        ProductMediaUrlCacheProperties properties = new ProductMediaUrlCacheProperties();
        properties.setKeyPrefix("product:media:url:");
        properties.setTtlSeconds(600);
        properties.setSafetyMarginSeconds(30);

        OssProperties ossProperties = new OssProperties();
        ossProperties.setPresignGetExpireSeconds(300);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("product:media:url:product/1001/b.jpg")).thenReturn(null);
        when(delegate.presignGetUrl("product/1001/b.jpg")).thenReturn("http://signed/b");

        ProductCachedOssObjectService service =
                new ProductCachedOssObjectService(delegate, stringRedisTemplate, properties, ossProperties);

        service.presignGetUrl("product/1001/b.jpg");

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("product:media:url:product/1001/b.jpg"), eq("http://signed/b"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(270));
    }

    @Test
    void shouldBypassCacheWhenRedisFails() {
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(delegate.presignGetUrl("product/1001/a.jpg")).thenReturn("http://signed/url");

        String url = cachedOssObjectService.presignGetUrl("product/1001/a.jpg");

        assertThat(url).isEqualTo("http://signed/url");
    }

    @Test
    void shouldDelegateUploadWithoutCaching() {
        OssObjectService.PresignUploadResult result = new OssObjectService.PresignUploadResult(
                "product/1001/a.jpg",
                "http://upload",
                123L,
                Map.of("Content-Type", "image/jpeg")
        );
        when(delegate.presignUpload(1001L, "a.jpg", "image/jpeg")).thenReturn(result);

        OssObjectService.PresignUploadResult response =
                cachedOssObjectService.presignUpload(1001L, "a.jpg", "image/jpeg");

        assertThat(response).isEqualTo(result);
    }
}
