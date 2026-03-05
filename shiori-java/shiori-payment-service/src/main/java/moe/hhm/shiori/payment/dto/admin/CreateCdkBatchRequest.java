package moe.hhm.shiori.payment.dto.admin;

import java.time.LocalDateTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateCdkBatchRequest(
        @NotNull(message = "quantity不能为空")
        @Min(value = 1, message = "quantity最小为1")
        @Max(value = 500, message = "quantity最大为500")
        Integer quantity,

        @NotNull(message = "amountCent不能为空")
        @Min(value = 1, message = "amountCent必须大于0")
        Long amountCent,

        LocalDateTime expireAt
) {
}
