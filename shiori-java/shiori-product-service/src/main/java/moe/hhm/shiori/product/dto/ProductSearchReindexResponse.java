package moe.hhm.shiori.product.dto;

public record ProductSearchReindexResponse(
        long reindexedCount,
        int batchCount,
        Long lastProductId
) {
}
