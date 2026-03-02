package moe.hhm.shiori.product.media.dto;

import java.util.Map;

public record PresignUploadResponse(
        String objectKey,
        String uploadUrl,
        long expireAt,
        Map<String, String> requiredHeaders
) {
}
