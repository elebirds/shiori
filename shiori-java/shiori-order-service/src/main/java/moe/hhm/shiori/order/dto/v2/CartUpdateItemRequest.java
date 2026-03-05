package moe.hhm.shiori.order.dto.v2;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartUpdateItemRequest(
        @NotNull(message = "quantity不能为空")
        @Min(value = 1, message = "quantity必须大于0")
        Integer quantity
) {
}
