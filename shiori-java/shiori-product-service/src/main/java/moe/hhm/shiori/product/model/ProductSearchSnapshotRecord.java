package moe.hhm.shiori.product.model;

import java.time.LocalDateTime;

public record ProductSearchSnapshotRecord(
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
        Integer isDeleted,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
