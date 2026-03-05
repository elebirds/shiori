package moe.hhm.shiori.order.dto.v2;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartAddItemRequest(
        @NotNull(message = "productId不能为空")
        Long productId,
        @NotNull(message = "skuId不能为空")
        Long skuId,
        @NotNull(message = "quantity不能为空")
        @Min(value = 1, message = "quantity必须大于0")
        Integer quantity
) {
}
