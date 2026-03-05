package moe.hhm.shiori.order.dto.v2;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminOrderReviewVisibilityRequest(
        @NotNull Boolean visible,
        @Size(max = 280) String reason
) {
}

