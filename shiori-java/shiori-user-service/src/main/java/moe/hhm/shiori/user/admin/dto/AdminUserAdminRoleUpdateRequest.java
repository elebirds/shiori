package moe.hhm.shiori.user.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AdminUserAdminRoleUpdateRequest(
        @NotNull(message = "grantAdmin 不能为空")
        Boolean grantAdmin,
        String reason
) {
}
