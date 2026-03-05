package moe.hhm.shiori.user.event;

public record UserAuthzChangedPayload(
        Long userId,
        Long version,
        String changedAt,
        String reason,
        Long operatorUserId
) {
}
