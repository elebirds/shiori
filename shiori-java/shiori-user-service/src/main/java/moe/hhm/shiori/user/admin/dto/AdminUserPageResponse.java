package moe.hhm.shiori.user.admin.dto;

import java.util.List;

public record AdminUserPageResponse(
        long total,
        int page,
        int size,
        List<AdminUserSummaryResponse> items
) {
}
