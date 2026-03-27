package moe.hhm.shiori.search.event;

import tools.jackson.databind.JsonNode;

public record EventEnvelope(
        String eventId,
        String type,
        String aggregateId,
        String createdAt,
        JsonNode payload
) {
}
