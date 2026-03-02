package moe.hhm.shiori.user.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称长度不能超过64")
        String nickname,
        @Size(max = 255, message = "头像地址长度不能超过255")
        String avatarUrl
) {
}
