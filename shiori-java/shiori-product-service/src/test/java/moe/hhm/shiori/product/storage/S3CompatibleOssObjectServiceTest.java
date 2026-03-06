package moe.hhm.shiori.product.storage;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.storage.OssObjectService;
import moe.hhm.shiori.common.storage.OssProperties;
import moe.hhm.shiori.common.storage.S3CompatibleOssObjectService;
import moe.hhm.shiori.common.storage.S3PresignClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3CompatibleOssObjectServiceTest {

    @Test
    void shouldGenerateObjectKeyWithFixedPattern() {
        FakeS3PresignClient fakeClient = new FakeS3PresignClient();
        OssProperties properties = defaultProperties();
        S3CompatibleOssObjectService service = new S3CompatibleOssObjectService(properties, fakeClient);

        OssObjectService.PresignUploadResult result = service.presignUpload(1001L, "cover.JPG", "image/jpeg");

        Pattern pattern = Pattern.compile("^product/1001/\\d{6}/[a-f0-9]{32}\\.jpg$");
        assertThat(result.objectKey()).matches(pattern);
        assertThat(result.uploadUrl()).isEqualTo("http://upload.local/url");
        assertThat(result.requiredHeaders()).containsEntry("Content-Type", "image/jpeg");
    }

    @Test
    void shouldRejectInvalidExtension() {
        FakeS3PresignClient fakeClient = new FakeS3PresignClient();
        S3CompatibleOssObjectService service = new S3CompatibleOssObjectService(defaultProperties(), fakeClient);

        assertThatThrownBy(() -> service.presignUpload(1001L, "cover.exe", "application/octet-stream"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldExposeExpireAtAndPassConfiguredDuration() {
        FakeS3PresignClient fakeClient = new FakeS3PresignClient();
        OssProperties properties = defaultProperties();
        properties.setPresignPutExpireSeconds(300);
        S3CompatibleOssObjectService service = new S3CompatibleOssObjectService(properties, fakeClient);

        long now = System.currentTimeMillis();
        OssObjectService.PresignUploadResult result = service.presignUpload(1001L, "cover.png", "image/png");

        assertThat(result.expireAt()).isBetween(now + 299_000L, now + 301_000L);
        assertThat(fakeClient.lastPutDuration).isEqualTo(Duration.ofSeconds(300));
        assertThat(result.requiredHeaders()).containsEntry("Content-Type", "image/png");
    }

    private OssProperties defaultProperties() {
        OssProperties properties = new OssProperties();
        properties.setBucket("shiori-product");
        properties.setPresignPutExpireSeconds(300);
        properties.setPresignGetExpireSeconds(300);
        return properties;
    }

    private static class FakeS3PresignClient implements S3PresignClient {
        private Duration lastPutDuration;

        @Override
        public PresignResult presignPutObject(String bucket, String objectKey, String contentType, Duration expiresIn) {
            this.lastPutDuration = expiresIn;
            return new PresignResult("http://upload.local/url", Map.of("Content-Type", contentType));
        }

        @Override
        public String presignGetObject(String bucket, String objectKey, Duration expiresIn) {
            return "http://download.local/url";
        }
    }
}
