package moe.hhm.shiori.product.dto.v2;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductV2BatchOffShelfRequest(
        @NotEmpty(message = "商品ID列表不能为空")
        @Size(max = 200, message = "批量数量不能超过200")
        List<Long> productIds,
        @Size(max = 255, message = "原因长度不能超过255")
        String reason
) {
}
