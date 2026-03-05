package moe.hhm.shiori.user.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record AdminUserCapabilityBanUpsertRequest(
        @NotBlank(message = "capability 不能为空")
        String capability,
        @Size(max = 255, message = "reason 长度不能超过 255")
        String reason,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
