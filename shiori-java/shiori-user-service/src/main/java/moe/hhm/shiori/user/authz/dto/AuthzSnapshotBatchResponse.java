package moe.hhm.shiori.user.authz.dto;

import java.util.List;

public record AuthzSnapshotBatchResponse(
        List<AuthzSnapshotResponse> snapshots
) {
}
