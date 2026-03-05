package moe.hhm.shiori.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecItemInput(
        @NotBlank(message = "规格名不能为空")
        @Size(max = 64, message = "规格名长度不能超过64")
        String name,
        @NotBlank(message = "规格值不能为空")
        @Size(max = 64, message = "规格值长度不能超过64")
        String value
) {
}
