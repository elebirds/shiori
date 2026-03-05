package moe.hhm.shiori.order.dto.v2;

import java.util.List;

public record CartResponse(
        Long cartId,
        Long sellerUserId,
        Integer totalItemCount,
        Long totalAmountCent,
        List<CartItemResponse> items
) {
}
