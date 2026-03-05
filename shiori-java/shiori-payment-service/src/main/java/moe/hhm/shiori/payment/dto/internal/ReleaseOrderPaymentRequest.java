package moe.hhm.shiori.payment.dto.internal;

import jakarta.validation.constraints.Size;

public record ReleaseOrderPaymentRequest(
        @Size(max = 255, message = "reason长度不能超过255")
        String reason
) {
}
