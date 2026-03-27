package moe.hhm.shiori.product.event;

public record ProductSearchUpsertedPayload(
        Long productId,
        String productNo,
        Long ownerUserId,
        String title,
        String description,
        String coverObjectKey,
        String categoryCode,
        String subCategoryCode,
        String conditionLevel,
        String tradeMode,
        String campusCode,
        Long minPriceCent,
        Long maxPriceCent,
        Integer totalStock,
        Integer status,
        Long version,
        String createdAt,
        String occurredAt
) {
}
