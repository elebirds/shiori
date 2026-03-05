package moe.hhm.shiori.order.dto.v2;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CartCheckoutRequest(
        List<@Positive(message = "itemId必须大于0") Long> itemIds,
        @Size(max = 32, message = "source长度不能超过32") String source,
        @Positive(message = "conversationId必须大于0") Long conversationId
) {
}
