package moe.hhm.shiori.user.event;

public record UserRoleChangedPayload(
        Long targetUserId,
        String roleCode,
        boolean granted,
        Long operatorUserId,
        String reason
) {
}
