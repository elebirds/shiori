package moe.hhm.shiori.payment.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundOrderPaymentRequest(
        @NotBlank(message = "refundNo不能为空")
        @Size(max = 64, message = "refundNo长度不能超过64")
        String refundNo,

        @NotBlank(message = "operatorType不能为空")
        @Size(max = 32, message = "operatorType长度不能超过32")
        String operatorType,

        Long operatorUserId,

        @Size(max = 255, message = "reason长度不能超过255")
        String reason
) {
}
