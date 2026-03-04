package moe.hhm.shiori.user.event;

public record UserStatusChangedPayload(
        Long targetUserId,
        String beforeStatus,
        String afterStatus,
        Long operatorUserId,
        String reason
) {
}
