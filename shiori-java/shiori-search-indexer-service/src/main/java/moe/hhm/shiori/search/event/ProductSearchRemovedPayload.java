package moe.hhm.shiori.search.event;

public record ProductSearchRemovedPayload(
        Long productId,
        String productNo,
        Integer status,
        Long version,
        String occurredAt,
        String reason
) {
}
