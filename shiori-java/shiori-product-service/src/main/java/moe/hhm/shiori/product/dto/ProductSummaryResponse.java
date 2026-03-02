package moe.hhm.shiori.product.dto;

public record ProductSummaryResponse(
        Long productId,
        String productNo,
        String title,
        String description,
        String coverObjectKey,
        String coverImageUrl,
        String status
) {
}
