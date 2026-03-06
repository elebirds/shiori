package moe.hhm.shiori.order.dto.v2;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateOrderFulfillmentRequest(
        @Size(min = 4, max = 16, message = "fulfillmentMode长度非法")
        String fulfillmentMode,
        @Positive(message = "addressId必须大于0")
        Long addressId
) {
}
