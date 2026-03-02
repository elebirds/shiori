package moe.hhm.shiori.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SkuInput(
        Long id,
        @NotBlank(message = "SKU名称不能为空")
        @Size(max = 120, message = "SKU名称长度不能超过120")
        String skuName,
        @Size(max = 1024, message = "规格描述长度不能超过1024")
        String specJson,
        @NotNull(message = "价格不能为空")
        @Min(value = 1, message = "价格必须大于0")
        Long priceCent,
        @NotNull(message = "库存不能为空")
        @Min(value = 0, message = "库存不能为负")
        Integer stock
) {
}
