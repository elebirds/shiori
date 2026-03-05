package moe.hhm.shiori.order.dto.v2;

import java.util.List;

public record CartItemResponse(
        Long itemId,
        Long productId,
        String productNo,
        String productTitle,
        String coverImageUrl,
        Long skuId,
        String skuNo,
        String displayName,
        List<CartSpecItemResponse> specItems,
        Long priceCent,
        Integer stock,
        Integer quantity,
        Long subtotalCent
) {
}
