package moe.hhm.shiori.social.storage;

import java.util.Map;

public interface OssObjectService {

    PresignUploadResult presignUpload(Long ownerUserId, String fileName, String contentType);

    String presignGetUrl(String objectKey);

    record PresignUploadResult(
            String objectKey,
            String uploadUrl,
            long expireAt,
            Map<String, String> requiredHeaders
    ) {
    }
}
