package moe.hhm.shiori.user.authz.dto;

import java.time.Instant;
import java.util.List;

public record AuthzSnapshotResponse(
        Long userId,
        Long version,
        List<String> grants,
        List<String> denies,
        Instant generatedAt,
        Instant expireAt
) {
}
