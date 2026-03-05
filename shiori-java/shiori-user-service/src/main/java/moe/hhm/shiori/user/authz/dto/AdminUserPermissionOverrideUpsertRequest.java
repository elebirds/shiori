package moe.hhm.shiori.user.authz.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record AdminUserPermissionOverrideUpsertRequest(
        @NotBlank(message = "permissionCode 不能为空")
        String permissionCode,
        @NotBlank(message = "effect 不能为空")
        String effect,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String reason
) {
}
