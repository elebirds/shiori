package moe.hhm.shiori.user.admin.dto;

import java.util.List;

public record AdminUserAuditPageResponse(
        long total,
        int page,
        int size,
        List<AdminUserAuditItemResponse> items
) {
}
