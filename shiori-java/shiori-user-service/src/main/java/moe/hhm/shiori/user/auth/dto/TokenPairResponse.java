package moe.hhm.shiori.user.auth.dto;

public record TokenPairResponse(
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn,
        String tokenType,
        AuthUserInfo user
) {
}
