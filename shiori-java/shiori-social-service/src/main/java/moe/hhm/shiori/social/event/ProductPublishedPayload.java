package moe.hhm.shiori.social.event;

public record ProductPublishedPayload(
        Long productId,
        String productNo,
        Long ownerUserId,
        String title,
        String coverObjectKey,
        Long minPriceCent,
        Long maxPriceCent,
        String campusCode
) {
}
