package moe.hhm.shiori.user.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "refreshToken不能为空") String refreshToken
) {
}
