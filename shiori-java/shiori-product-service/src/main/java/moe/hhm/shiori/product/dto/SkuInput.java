package moe.hhm.shiori.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;

public record SkuInput(
        Long id,
        @NotEmpty(message = "规格项不能为空")
        @Valid
        List<SpecItemInput> specItems,
        @NotNull(message = "价格不能为空")
        @Min(value = 1, message = "价格必须大于0")
        Long priceCent,
        @NotNull(message = "库存不能为空")
        @Min(value = 0, message = "库存不能为负")
        Integer stock
) {
}
