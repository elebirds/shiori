package moe.hhm.shiori.user.auth;

import java.util.List;
import moe.hhm.shiori.user.domain.UserStatus;

public record UserAuthView(
        Long userId,
        String userNo,
        String username,
        String passwordHash,
        UserStatus status,
        List<String> roles,
        List<String> permissions
) {
}
