package moe.hhm.shiori.order.client;

import java.util.List;

public record ProductDetailSnapshot(
        Long productId,
        String productNo,
        Long ownerUserId,
        String title,
        String description,
        String status,
        List<ProductSkuSnapshot> skus
) {
}
