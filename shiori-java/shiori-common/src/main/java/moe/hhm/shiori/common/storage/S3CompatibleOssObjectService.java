package moe.hhm.shiori.common.storage;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public class S3CompatibleOssObjectService implements OssObjectService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Map<String, String> DEFAULT_CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    private final OssProperties properties;
    private final S3PresignClient s3PresignClient;

    public S3CompatibleOssObjectService(OssProperties properties, S3PresignClient s3PresignClient) {
        this.properties = properties;
        this.s3PresignClient = s3PresignClient;
    }

    @Override
    public PresignUploadResult presignUpload(Long ownerUserId, String fileName, String contentType) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new BizException(ProductErrorCode.INVALID_MEDIA_OBJECT_KEY, HttpStatus.BAD_REQUEST);
        }

        String extension = extractAndValidateExtension(fileName);
        String normalizedContentType = normalizeContentType(contentType, extension);
        String objectKey = buildObjectKey(ownerUserId, extension);
        long expireAt = System.currentTimeMillis() + properties.getPresignPutExpireSeconds() * 1000L;

        S3PresignClient.PresignResult result = s3PresignClient.presignPutObject(
                properties.getBucket(),
                objectKey,
                normalizedContentType,
                Duration.ofSeconds(properties.getPresignPutExpireSeconds())
        );
        return new PresignUploadResult(objectKey, result.url(), expireAt, result.requiredHeaders());
    }

    @Override
    public String presignGetUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        if (!isObjectKeySafe(objectKey)) {
            throw new BizException(ProductErrorCode.INVALID_MEDIA_OBJECT_KEY, HttpStatus.BAD_REQUEST);
        }
        return s3PresignClient.presignGetObject(
                properties.getBucket(),
                objectKey,
                Duration.ofSeconds(properties.getPresignGetExpireSeconds())
        );
    }

    String buildObjectKey(Long ownerUserId, String extension) {
        String yearMonth = LocalDate.now(ZoneOffset.UTC).format(YYYYMM);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "product/" + ownerUserId + "/" + yearMonth + "/" + uuid + "." + extension;
    }

    String extractAndValidateExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new BizException(ProductErrorCode.INVALID_MEDIA_OBJECT_KEY, HttpStatus.BAD_REQUEST);
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT)
                .trim();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ProductErrorCode.INVALID_MEDIA_OBJECT_KEY, HttpStatus.BAD_REQUEST);
        }
        return extension;
    }

    String normalizeContentType(String contentType, String extension) {
        String defaultType = DEFAULT_CONTENT_TYPES.get(extension);
        if (!StringUtils.hasText(contentType)) {
            return defaultType;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("image/")) {
            throw new BizException(ProductErrorCode.INVALID_MEDIA_OBJECT_KEY, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private boolean isObjectKeySafe(String objectKey) {
        return objectKey.startsWith("product/") && !objectKey.contains("..");
    }
}
