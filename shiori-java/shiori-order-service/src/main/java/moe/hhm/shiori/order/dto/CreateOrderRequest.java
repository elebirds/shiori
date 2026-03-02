package moe.hhm.shiori.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "订单项不能为空")
        List<@Valid CreateOrderItem> items
) {
}
