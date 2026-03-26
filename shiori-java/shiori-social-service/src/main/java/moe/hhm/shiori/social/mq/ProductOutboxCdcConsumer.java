package moe.hhm.shiori.social.mq;

import moe.hhm.shiori.social.event.EventEnvelope;
import moe.hhm.shiori.social.event.ProductPublishedPayload;
import moe.hhm.shiori.social.service.SocialPostService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "social.kafka", name = "enabled", havingValue = "true")
public class ProductOutboxCdcConsumer {

    private final ObjectMapper objectMapper;
    private final SocialPostService socialPostService;

    public ProductOutboxCdcConsumer(ObjectMapper objectMapper, SocialPostService socialPostService) {
        this.objectMapper = objectMapper;
        this.socialPostService = socialPostService;
    }

    @KafkaListener(
            topics = "${social.kafka.product-outbox-topic:shiori.cdc.product.outbox.raw}",
            groupId = "${social.kafka.product-outbox-group-id:shiori-social-product-cdc}"
    )
    public void onMessage(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return;
        }
        JsonNode cdcRecord = parseCdcRecord(rawMessage);
        if (!"PENDING".equalsIgnoreCase(cdcRecord.path("status").asText())) {
            return;
        }
        validateCdcRecord(cdcRecord);

        EventEnvelope envelope = parseEnvelope(cdcRecord.path("payload").asText());
        ProductPublishedPayload payload = parsePayload(envelope);
        if (!StringUtils.hasText(envelope.eventId())
                || payload.ownerUserId() == null || payload.ownerUserId() <= 0
                || payload.productId() == null || payload.productId() <= 0) {
            throw new IllegalArgumentException("invalid product published event");
        }
        socialPostService.handleProductPublished(envelope.eventId(), payload);
    }

    private JsonNode parseCdcRecord(String rawMessage) {
        try {
            return objectMapper.readTree(rawMessage);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid product outbox cdc record", ex);
        }
    }

    private void validateCdcRecord(JsonNode cdcRecord) {
        String aggregateType = cdcRecord.path("aggregate_type").asText(null);
        String type = cdcRecord.path("type").asText(null);
        String payload = cdcRecord.path("payload").asText(null);
        if (!"product".equalsIgnoreCase(aggregateType)) {
            throw new IllegalArgumentException("unsupported aggregate type: " + aggregateType);
        }
        if (!"PRODUCT_PUBLISHED".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("unsupported event type: " + type);
        }
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("empty outbox payload");
        }
    }

    private EventEnvelope parseEnvelope(String rawPayload) {
        try {
            EventEnvelope envelope = objectMapper.readValue(rawPayload, EventEnvelope.class);
            if (envelope == null || envelope.payload() == null) {
                throw new IllegalArgumentException("empty event envelope");
            }
            if (!"PRODUCT_PUBLISHED".equalsIgnoreCase(envelope.type())) {
                throw new IllegalArgumentException("unsupported event type: " + envelope.type());
            }
            return envelope;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid event envelope", ex);
        }
    }

    private ProductPublishedPayload parsePayload(EventEnvelope envelope) {
        try {
            return objectMapper.treeToValue(envelope.payload(), ProductPublishedPayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid event payload", ex);
        }
    }
}
