package moe.hhm.shiori.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockReleaseRequest(
        @NotNull(message = "skuId不能为空")
        Long skuId,
        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量必须大于0")
        Integer quantity,
        @NotBlank(message = "bizNo不能为空")
        @Size(max = 64, message = "bizNo长度不能超过64")
        String bizNo
) {
}
