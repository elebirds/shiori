package moe.hhm.shiori.product.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProductSubCategoryRequest(
        @NotBlank(message = "子分类名称不能为空")
        @Size(max = 128, message = "子分类名称长度不能超过128")
        String subCategoryName,
        Integer status,
        Integer sortOrder
) {
}
