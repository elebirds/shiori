package moe.hhm.shiori.product.dto;

import java.util.List;

public record ProductDetailResponse(
        Long productId,
        String productNo,
        Long ownerUserId,
        String title,
        String description,
        String coverObjectKey,
        String coverImageUrl,
        String status,
        List<SkuResponse> skus
) {
}
