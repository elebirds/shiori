package moe.hhm.shiori.product.admin.dto;

import java.util.List;

public record AdminProductMetaCategoryResponse(
        Long id,
        String categoryCode,
        String categoryName,
        Integer status,
        Integer sortOrder,
        List<AdminProductMetaSubCategoryResponse> subCategories
) {
}
