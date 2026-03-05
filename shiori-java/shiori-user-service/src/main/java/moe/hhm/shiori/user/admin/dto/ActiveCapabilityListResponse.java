package moe.hhm.shiori.user.admin.dto;

import java.util.List;

public record ActiveCapabilityListResponse(
        Long userId,
        List<String> capabilities
) {
}
