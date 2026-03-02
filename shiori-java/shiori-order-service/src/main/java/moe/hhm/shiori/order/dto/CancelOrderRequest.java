package moe.hhm.shiori.order.dto;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(
        @Size(max = 128, message = "reason长度不能超过128")
        String reason
) {
}
