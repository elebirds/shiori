package moe.hhm.shiori.user.authz.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AuthzSnapshotBatchRequest(
        @NotEmpty(message = "userIds 不能为空")
        List<Long> userIds
) {
}
