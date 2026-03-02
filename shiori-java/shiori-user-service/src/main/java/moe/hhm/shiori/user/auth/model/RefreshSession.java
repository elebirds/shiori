package moe.hhm.shiori.user.auth.model;

import java.util.List;

public record RefreshSession(
        Long userId,
        String userNo,
        String username,
        List<String> roles,
        long issuedAt
) {
}
