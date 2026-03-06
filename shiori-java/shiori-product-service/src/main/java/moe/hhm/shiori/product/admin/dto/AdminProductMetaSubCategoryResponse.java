package moe.hhm.shiori.product.admin.dto;

public record AdminProductMetaSubCategoryResponse(
        Long id,
        String categoryCode,
        String subCategoryCode,
        String subCategoryName,
        Integer status,
        Integer sortOrder
) {
}
