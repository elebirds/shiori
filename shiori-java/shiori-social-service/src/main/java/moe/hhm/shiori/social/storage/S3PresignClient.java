package moe.hhm.shiori.social.storage;

import java.time.Duration;
import java.util.Map;

public interface S3PresignClient {

    PresignResult presignPutObject(String bucket, String objectKey, String contentType, Duration expiresIn);

    String presignGetObject(String bucket, String objectKey, Duration expiresIn);

    record PresignResult(String url, Map<String, String> requiredHeaders) {
    }
}
