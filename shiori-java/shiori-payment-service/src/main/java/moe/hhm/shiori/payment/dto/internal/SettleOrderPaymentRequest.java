package moe.hhm.shiori.payment.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SettleOrderPaymentRequest(
        @NotBlank(message = "operatorType不能为空")
        @Size(max = 32, message = "operatorType长度不能超过32")
        String operatorType,
        Long operatorUserId
) {
}
