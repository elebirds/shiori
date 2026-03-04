package moe.hhm.shiori.user.event;

public record UserPasswordResetPayload(
        Long targetUserId,
        Long operatorUserId,
        boolean mustChangePassword,
        String reason
) {
}
