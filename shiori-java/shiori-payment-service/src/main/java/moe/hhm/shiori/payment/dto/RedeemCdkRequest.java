package moe.hhm.shiori.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedeemCdkRequest(
        @NotBlank(message = "code不能为空")
        @Size(max = 128, message = "code长度不能超过128")
        String code
) {
}
