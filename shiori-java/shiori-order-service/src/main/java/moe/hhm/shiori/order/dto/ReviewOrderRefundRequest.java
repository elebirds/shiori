package moe.hhm.shiori.order.dto;

import jakarta.validation.constraints.Size;

public record ReviewOrderRefundRequest(
        @Size(max = 255, message = "reason长度不能超过255")
        String reason
) {
}
