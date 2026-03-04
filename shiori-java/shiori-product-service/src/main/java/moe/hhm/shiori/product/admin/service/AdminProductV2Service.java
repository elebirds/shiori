package moe.hhm.shiori.product.admin.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2BatchOffShelfResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminProductV2Service {

    private final AdminProductService adminProductService;

    public AdminProductV2Service(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductV2BatchOffShelfResponse batchForceOffShelf(List<Long> productIds, Long operatorUserId, String reason) {
        Set<Long> uniqueIds = new LinkedHashSet<>();
        if (productIds != null) {
            for (Long productId : productIds) {
                if (productId != null && productId > 0) {
                    uniqueIds.add(productId);
                }
            }
        }

        List<Long> failedProductIds = new ArrayList<>();
        List<ProductWriteResponse> successItems = new ArrayList<>();
        for (Long productId : uniqueIds) {
            try {
                ProductWriteResponse response = adminProductService.forceOffShelf(productId, operatorUserId, reason);
                successItems.add(response);
            } catch (RuntimeException ex) {
                failedProductIds.add(productId);
            }
        }

        return new ProductV2BatchOffShelfResponse(
                uniqueIds.size(),
                successItems.size(),
                failedProductIds,
                successItems
        );
    }
}
