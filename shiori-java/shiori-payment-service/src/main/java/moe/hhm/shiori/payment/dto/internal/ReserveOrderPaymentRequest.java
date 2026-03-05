package moe.hhm.shiori.payment.dto.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReserveOrderPaymentRequest(
        @NotNull(message = "buyerUserId不能为空")
        @Min(value = 1, message = "buyerUserId非法")
        Long buyerUserId,

        @NotNull(message = "sellerUserId不能为空")
        @Min(value = 1, message = "sellerUserId非法")
        Long sellerUserId,

        @NotNull(message = "amountCent不能为空")
        @Min(value = 1, message = "amountCent必须大于0")
        Long amountCent
) {
}
