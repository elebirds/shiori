package moe.hhm.shiori.product.service;

import java.util.List;
import moe.hhm.shiori.product.dto.ProductSearchReindexResponse;
import moe.hhm.shiori.product.repository.ProductMapper;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchReindexService {

    private static final int DEFAULT_BATCH_SIZE = 200;

    private final ProductMapper productMapper;
    private final ProductService productService;

    public ProductSearchReindexService(ProductMapper productMapper, ProductService productService) {
        this.productMapper = productMapper;
        this.productService = productService;
    }

    public ProductSearchReindexResponse reindexAllOnSaleProducts(int batchSize) {
        int normalizedBatchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        Long lastProductId = null;
        long reindexedCount = 0;
        int batchCount = 0;

        while (true) {
            List<Long> productIds = productMapper.listOnSaleProductIdsAfterId(lastProductId, normalizedBatchSize);
            if (productIds == null || productIds.isEmpty()) {
                break;
            }
            batchCount++;
            for (Long productId : productIds) {
                productService.appendProductSearchUpsertOutbox(productId);
                reindexedCount++;
                lastProductId = productId;
            }
            if (productIds.size() < normalizedBatchSize) {
                break;
            }
        }

        return new ProductSearchReindexResponse(reindexedCount, batchCount, lastProductId);
    }
}
