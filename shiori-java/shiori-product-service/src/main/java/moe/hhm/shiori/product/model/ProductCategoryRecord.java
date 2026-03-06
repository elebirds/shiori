package moe.hhm.shiori.product.model;

public record ProductCategoryRecord(
        Long id,
        String categoryCode,
        String categoryName,
        Integer status,
        Integer sortOrder,
        Integer isDeleted
) {
}
