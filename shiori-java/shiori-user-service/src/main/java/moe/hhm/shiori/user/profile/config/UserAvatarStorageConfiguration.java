package moe.hhm.shiori.user.profile.config;

import java.net.URI;
import moe.hhm.shiori.user.profile.storage.S3UserAvatarStorageService;
import moe.hhm.shiori.user.profile.storage.UserAvatarStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(UserAvatarStorageProperties.class)
@ConditionalOnProperty(prefix = "user.avatar.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UserAvatarStorageConfiguration {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(UserAvatarStorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
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
    public UserAvatarStorageService userAvatarStorageService(UserAvatarStorageProperties properties,
                                                             S3Client s3Client) {
        return new S3UserAvatarStorageService(properties, s3Client);
    }
}
