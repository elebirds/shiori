package moe.hhm.shiori.product.media.dto;

import java.util.Map;

public record ResolveMediaUrlsResponse(
        Map<String, String> urls
) {
}
