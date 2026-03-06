package moe.hhm.shiori.product.dto.v2;

public record PostRelatedProductResponse(
        Long productId,
        String productNo,
        String title,
        String coverObjectKey,
        String coverImageUrl,
        Long minPriceCent,
        Long maxPriceCent,
        String campusCode
) {
}
