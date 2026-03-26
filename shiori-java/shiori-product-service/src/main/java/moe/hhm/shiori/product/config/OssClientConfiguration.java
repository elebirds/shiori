package moe.hhm.shiori.product.config;

import java.net.URI;
import moe.hhm.shiori.common.storage.AwsS3PresignClient;
import moe.hhm.shiori.common.storage.OssObjectService;
import moe.hhm.shiori.common.storage.OssProperties;
import moe.hhm.shiori.common.storage.S3CompatibleOssObjectService;
import moe.hhm.shiori.common.storage.S3PresignClient;
import moe.hhm.shiori.product.service.ProductCachedOssObjectService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties({OssProperties.class, ProductMediaUrlCacheProperties.class})
@ConditionalOnProperty(prefix = "storage.oss", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OssClientConfiguration {

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(OssProperties properties) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));

        if (StringUtils.hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    public S3PresignClient s3PresignClient(S3Presigner s3Presigner) {
        return new AwsS3PresignClient(s3Presigner);
    }

    @Bean
    public OssObjectService baseOssObjectService(OssProperties properties, S3PresignClient s3PresignClient) {
        return new S3CompatibleOssObjectService(properties, s3PresignClient);
    }

    @Bean
    @Primary
    public OssObjectService ossObjectService(@Qualifier("baseOssObjectService") OssObjectService delegate,
                                             @Nullable @Qualifier("stringRedisTemplate") StringRedisTemplate stringRedisTemplate,
                                             ProductMediaUrlCacheProperties cacheProperties,
                                             OssProperties ossProperties) {
        return new ProductCachedOssObjectService(delegate, stringRedisTemplate, cacheProperties, ossProperties);
    }
}
