package moe.hhm.shiori.order.event;

import tools.jackson.databind.JsonNode;

public record EventEnvelope(
        String eventId,
        String type,
        String aggregateId,
        String createdAt,
        JsonNode payload
) {
}
