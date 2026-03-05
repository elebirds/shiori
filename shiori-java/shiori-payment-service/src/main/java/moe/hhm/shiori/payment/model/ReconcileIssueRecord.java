package moe.hhm.shiori.payment.model;

import java.time.LocalDateTime;

public record ReconcileIssueRecord(
        Long id,
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
