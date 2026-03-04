package moe.hhm.shiori.product.dto.v2;

import java.util.List;
import moe.hhm.shiori.product.dto.SkuResponse;

public record ProductV2DetailResponse(
        Long productId,
        String productNo,
        Long ownerUserId,
        String title,
        String description,
        String detailHtml,
        String coverObjectKey,
        String coverImageUrl,
        String status,
        String categoryCode,
        String conditionLevel,
        String tradeMode,
        String campusCode,
        Long minPriceCent,
        Long maxPriceCent,
        Integer totalStock,
        List<SkuResponse> skus
) {
}
