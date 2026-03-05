package moe.hhm.shiori.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "订单项不能为空") List<@Valid CreateOrderItem> items,
        @Size(max = 32, message = "source长度不能超过32") String source,
        @Positive(message = "conversationId必须大于0") Long conversationId
) {
}
