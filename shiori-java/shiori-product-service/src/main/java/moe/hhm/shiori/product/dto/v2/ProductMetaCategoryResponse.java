package moe.hhm.shiori.product.dto.v2;

import java.util.List;

public record ProductMetaCategoryResponse(
        String categoryCode,
        String categoryName,
        List<ProductMetaSubCategoryResponse> subCategories
) {
}
