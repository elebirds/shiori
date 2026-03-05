package moe.hhm.shiori.order.dto.v2;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderReviewUpsertRequest(
        @NotNull @Min(1) @Max(5) Integer communicationStar,
        @NotNull @Min(1) @Max(5) Integer timelinessStar,
        @NotNull @Min(1) @Max(5) Integer credibilityStar,
        @Size(max = 280) String comment
) {
}
