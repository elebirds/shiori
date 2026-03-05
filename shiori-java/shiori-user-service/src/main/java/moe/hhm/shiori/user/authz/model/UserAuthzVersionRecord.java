package moe.hhm.shiori.user.authz.model;

import java.time.LocalDateTime;

public record UserAuthzVersionRecord(
        Long userId,
        Long version,
        LocalDateTime updatedAt
) {
}
