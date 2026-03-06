package moe.hhm.shiori.product.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MigrateProductSubCategoryRequest(
        @NotBlank(message = "来源子分类编码不能为空")
        @Size(max = 64, message = "来源子分类编码长度不能超过64")
        String fromSubCategoryCode,
        @NotBlank(message = "目标子分类编码不能为空")
        @Size(max = 64, message = "目标子分类编码长度不能超过64")
        String toSubCategoryCode
) {
}
