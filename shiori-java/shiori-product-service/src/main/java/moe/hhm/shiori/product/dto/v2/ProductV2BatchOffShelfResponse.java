package moe.hhm.shiori.product.dto.v2;

import java.util.List;
import moe.hhm.shiori.product.dto.ProductWriteResponse;

public record ProductV2BatchOffShelfResponse(
        int total,
        int successCount,
        List<Long> failedProductIds,
        List<ProductWriteResponse> successItems
) {
}
