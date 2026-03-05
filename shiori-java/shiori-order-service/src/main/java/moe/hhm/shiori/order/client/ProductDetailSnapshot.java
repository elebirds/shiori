package moe.hhm.shiori.order.client;

import java.util.List;

public record ProductDetailSnapshot(
        Long productId,
        String productNo,
        Long ownerUserId,
        String title,
        String description,
        String coverImageUrl,
        String status,
        List<ProductSkuSnapshot> skus
) {
    public ProductDetailSnapshot(Long productId,
                                 String productNo,
                                 Long ownerUserId,
                                 String title,
                                 String description,
                                 String status,
                                 List<ProductSkuSnapshot> skus) {
        this(productId, productNo, ownerUserId, title, description, null, status, skus);
    }
}
