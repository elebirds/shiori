package moe.hhm.shiori.product.model;

public record ProductSubCategoryRecord(
        Long id,
        String categoryCode,
        String subCategoryCode,
        String subCategoryName,
        Integer status,
        Integer sortOrder,
        Integer isDeleted
) {
}
