package moe.hhm.shiori.user.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserPasswordResetRequest(
        @NotBlank(message = "newPassword 不能为空")
        @Size(min = 8, max = 100, message = "newPassword 长度必须在 8-100 之间")
        String newPassword,
        Boolean forceChangePassword,
        String reason
) {
}
