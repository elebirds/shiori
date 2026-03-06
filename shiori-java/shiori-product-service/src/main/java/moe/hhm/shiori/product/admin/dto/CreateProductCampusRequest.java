package moe.hhm.shiori.product.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductCampusRequest(
        @NotBlank(message = "校区编码不能为空")
        @Size(max = 64, message = "校区编码长度不能超过64")
        String campusCode,
        @NotBlank(message = "校区名称不能为空")
        @Size(max = 128, message = "校区名称长度不能超过128")
        String campusName,
        Integer sortOrder
) {
}
