package moe.hhm.shiori.product.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductSubCategoryRequest(
        @NotBlank(message = "一级分类编码不能为空")
        @Size(max = 64, message = "一级分类编码长度不能超过64")
        String categoryCode,
        @NotBlank(message = "子分类编码不能为空")
        @Size(max = 64, message = "子分类编码长度不能超过64")
        String subCategoryCode,
        @NotBlank(message = "子分类名称不能为空")
        @Size(max = 128, message = "子分类名称长度不能超过128")
        String subCategoryName,
        Integer sortOrder
) {
}
