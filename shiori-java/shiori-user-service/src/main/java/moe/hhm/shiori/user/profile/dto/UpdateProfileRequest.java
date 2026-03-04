package moe.hhm.shiori.user.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称长度不能超过64")
        String nickname,
        @NotNull(message = "性别不能为空")
        Integer gender,
        @PastOrPresent(message = "出生日期不能晚于今天")
        LocalDate birthDate,
        @Size(max = 280, message = "个人简介长度不能超过280")
        String bio
) {
}
