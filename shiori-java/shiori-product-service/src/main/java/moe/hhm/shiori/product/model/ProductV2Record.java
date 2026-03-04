package moe.hhm.shiori.product.model;

public record ProductV2Record(
        Long id,
        String productNo,
        Long ownerUserId,
        String title,
        String description,
        String coverObjectKey,
        String categoryCode,
        String conditionLevel,
        String tradeMode,
        String campusCode,
        Integer status,
        Integer isDeleted,
        Long minPriceCent,
        Long maxPriceCent,
        Integer totalStock
) {
}
