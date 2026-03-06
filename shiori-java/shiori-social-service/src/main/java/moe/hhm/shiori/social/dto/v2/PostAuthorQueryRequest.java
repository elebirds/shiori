package moe.hhm.shiori.social.dto.v2;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostAuthorQueryRequest(
        @NotNull(message = "作者ID列表不能为空")
        @Size(max = 5000, message = "作者数量不能超过5000")
        List<@NotNull(message = "作者ID不能为空") @Positive(message = "作者ID必须大于0") Long> authorUserIds,
        @Min(value = 1, message = "页码必须大于0")
        Integer page,
        @Min(value = 1, message = "每页数量必须大于0")
        @Max(value = 50, message = "每页数量不能超过50")
        Integer size
) {
}
