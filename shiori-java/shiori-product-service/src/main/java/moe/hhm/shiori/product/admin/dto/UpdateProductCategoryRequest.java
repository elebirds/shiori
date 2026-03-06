package moe.hhm.shiori.product.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProductCategoryRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 128, message = "分类名称长度不能超过128")
        String categoryName,
        Integer status,
        Integer sortOrder
) {
}
