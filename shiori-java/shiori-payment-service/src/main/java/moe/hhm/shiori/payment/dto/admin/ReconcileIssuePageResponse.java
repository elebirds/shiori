package moe.hhm.shiori.payment.dto.admin;

import java.util.List;

public record ReconcileIssuePageResponse(
        Long total,
        Integer page,
        Integer size,
        List<ReconcileIssueResponse> items
) {
}
