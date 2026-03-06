package moe.hhm.shiori.payment.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateReconcileIssueStatusRequest(
        @NotBlank(message = "fromStatus不能为空")
        @Size(max = 16, message = "fromStatus长度不能超过16")
        String fromStatus,

        @NotBlank(message = "toStatus不能为空")
        @Size(max = 16, message = "toStatus长度不能超过16")
        String toStatus
) {
}
