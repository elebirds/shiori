package moe.hhm.shiori.product.dto;

import java.util.List;

public record SkuResponse(
        Long skuId,
        String skuNo,
        String displayName,
        List<SpecItemResponse> specItems,
        Long priceCent,
        Integer stock
) {
}
