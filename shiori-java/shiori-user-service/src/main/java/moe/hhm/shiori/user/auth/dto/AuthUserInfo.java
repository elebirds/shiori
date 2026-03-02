package moe.hhm.shiori.user.auth.dto;

import java.util.List;

public record AuthUserInfo(
        Long userId,
        String userNo,
        String username,
        List<String> roles
) {
}
