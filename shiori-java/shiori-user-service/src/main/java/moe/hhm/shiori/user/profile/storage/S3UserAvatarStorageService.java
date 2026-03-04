package moe.hhm.shiori.user.profile.storage;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.profile.config.UserAvatarStorageProperties;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3UserAvatarStorageService implements UserAvatarStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> DEFAULT_CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Pattern AVATAR_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,200}$");

    private final UserAvatarStorageProperties properties;
    private final S3Client s3Client;

    public S3UserAvatarStorageService(UserAvatarStorageProperties properties, S3Client s3Client) {
        this.properties = properties;
        this.s3Client = s3Client;
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (userId == null || userId <= 0 || file == null || file.isEmpty()) {
            throw new BizException(UserErrorCode.AVATAR_FILE_INVALID, HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BizException(UserErrorCode.AVATAR_FILE_INVALID, HttpStatus.BAD_REQUEST);
        }

        String extension = extractAndValidateExtension(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType(), extension);
        String avatarKey = buildAvatarKey(userId, extension);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(avatarKey)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return avatarKey;
        } catch (IOException | S3Exception ex) {
            throw new BizException(UserErrorCode.AVATAR_UPLOAD_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public AvatarObject loadAvatar(String avatarKey) {
        if (!isAvatarKeySafe(avatarKey)) {
            throw new BizException(UserErrorCode.AVATAR_FILE_INVALID, HttpStatus.BAD_REQUEST);
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(avatarKey)
                    .build();
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            String contentType = response.response().contentType();
            if (!StringUtils.hasText(contentType)) {
                contentType = inferContentType(avatarKey);
            }
            return new AvatarObject(response.asByteArray(), contentType);
        } catch (NoSuchKeyException ex) {
            throw new BizException(UserErrorCode.AVATAR_NOT_FOUND, HttpStatus.NOT_FOUND);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new BizException(UserErrorCode.AVATAR_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            throw new BizException(UserErrorCode.AVATAR_READ_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    String extractAndValidateExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new BizException(UserErrorCode.AVATAR_FILE_INVALID, HttpStatus.BAD_REQUEST);
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT)
                .trim();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(UserErrorCode.AVATAR_FILE_INVALID, HttpStatus.BAD_REQUEST);
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
            throw new BizException(UserErrorCode.AVATAR_FILE_INVALID, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    String buildAvatarKey(Long userId, String extension) {
        String yearMonth = LocalDate.now(ZoneOffset.UTC).format(YYYYMM);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "avatar_" + userId + "_" + yearMonth + "_" + uuid + "." + extension;
    }

    private boolean isAvatarKeySafe(String avatarKey) {
        return StringUtils.hasText(avatarKey) && AVATAR_KEY_PATTERN.matcher(avatarKey).matches();
    }

    private String inferContentType(String avatarKey) {
        String extension = avatarKey.substring(avatarKey.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return DEFAULT_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }
}
