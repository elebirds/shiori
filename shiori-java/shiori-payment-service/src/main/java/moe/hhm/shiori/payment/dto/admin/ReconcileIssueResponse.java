package moe.hhm.shiori.payment.dto.admin;

import java.time.LocalDateTime;

public record ReconcileIssueResponse(
        String issueNo,
        String issueType,
        String bizNo,
        String severity,
        String status,
        String detailJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
