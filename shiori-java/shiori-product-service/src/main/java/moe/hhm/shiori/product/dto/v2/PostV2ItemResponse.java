package moe.hhm.shiori.product.dto.v2;

import java.time.LocalDateTime;

public record PostV2ItemResponse(
        Long postId,
        String postNo,
        Long authorUserId,
        String sourceType,
        String contentHtml,
        PostRelatedProductResponse relatedProduct,
        LocalDateTime createdAt
) {
}
