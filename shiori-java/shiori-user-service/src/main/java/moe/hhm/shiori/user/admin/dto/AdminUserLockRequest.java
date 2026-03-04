package moe.hhm.shiori.user.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminUserLockRequest(
        @Min(value = 1, message = "durationMinutes 最小为 1 分钟")
        @Max(value = 43200, message = "durationMinutes 最大为 43200 分钟")
        Long durationMinutes,
        String reason
) {
}
