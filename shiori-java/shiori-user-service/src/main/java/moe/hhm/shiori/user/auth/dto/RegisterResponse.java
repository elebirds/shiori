package moe.hhm.shiori.user.auth.dto;

public record RegisterResponse(
        Long userId,
        String userNo,
        String username,
        String nickname
) {
}
