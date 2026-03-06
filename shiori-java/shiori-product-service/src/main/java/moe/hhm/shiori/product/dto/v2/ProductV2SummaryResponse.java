package moe.hhm.shiori.product.dto.v2;

public record ProductV2SummaryResponse(
        Long productId,
        String productNo,
        String title,
        String description,
        String coverObjectKey,
        String coverImageUrl,
        String status,
        String categoryCode,
        String subCategoryCode,
        String conditionLevel,
        String tradeMode,
        String campusCode,
        Long minPriceCent,
        Long maxPriceCent,
        Integer totalStock
) {
}
