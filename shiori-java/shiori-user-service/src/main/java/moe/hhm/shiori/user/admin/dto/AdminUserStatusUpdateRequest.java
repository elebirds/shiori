package moe.hhm.shiori.user.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUserStatusUpdateRequest(
        @NotBlank(message = "status 不能为空")
        String status,
        String reason
) {
}
