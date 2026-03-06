package moe.hhm.shiori.social.model;

import java.time.LocalDateTime;

public record PostRecord(
        Long id,
        String postNo,
        Long authorUserId,
        String sourceType,
        String contentHtml,
        Long relatedProductId,
        String relatedProductNo,
        String relatedProductTitle,
        String relatedProductCoverObjectKey,
        Long relatedProductMinPriceCent,
        Long relatedProductMaxPriceCent,
        String relatedProductCampusCode,
        Integer isDeleted,
        LocalDateTime createdAt
) {
}
