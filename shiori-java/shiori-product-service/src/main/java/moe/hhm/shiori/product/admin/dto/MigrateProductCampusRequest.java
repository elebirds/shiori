package moe.hhm.shiori.product.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MigrateProductCampusRequest(
        @NotBlank(message = "来源校区编码不能为空")
        @Size(max = 64, message = "来源校区编码长度不能超过64")
        String fromCampusCode,
        @NotBlank(message = "目标校区编码不能为空")
        @Size(max = 64, message = "目标校区编码长度不能超过64")
        String toCampusCode
) {
}
