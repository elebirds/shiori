package moe.hhm.shiori.user.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "旧密码不能为空")
        @Size(max = 100, message = "旧密码长度不能超过100")
        String oldPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 100, message = "新密码长度必须在8-100之间")
        String newPassword
) {
}
