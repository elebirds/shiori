package moe.hhm.shiori.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PayOrderRequest(
        @NotBlank(message = "paymentNo不能为空")
        @Size(max = 64, message = "paymentNo长度不能超过64")
        String paymentNo
) {
}
