package moe.hhm.shiori.search.mq;

import moe.hhm.shiori.search.event.EventEnvelope;
import moe.hhm.shiori.search.event.ProductSearchRemovedPayload;
import moe.hhm.shiori.search.event.ProductSearchUpsertedPayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "search.kafka", name = "enabled", havingValue = "true")
public class ProductSearchOutboxCdcConsumer {

    private final ObjectMapper objectMapper;
    private final ProductSearchIndexingService indexingService;
    private final String consumerGroup;

    public ProductSearchOutboxCdcConsumer(ObjectMapper objectMapper,
                                          ProductSearchIndexingService indexingService,
                                          @Value("${search.kafka.product-outbox-group-id:shiori-search-indexer}")
                                          String consumerGroup) {
        this.objectMapper = objectMapper;
        this.indexingService = indexingService;
        this.consumerGroup = consumerGroup;
    }

    @KafkaListener(
            topics = "${search.kafka.product-outbox-topic:shiori.cdc.product.outbox.raw}",
            groupId = "${search.kafka.product-outbox-group-id:shiori-search-indexer}"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String message = record == null ? null : record.value();
        if (!StringUtils.hasText(message)) {
            acknowledge(acknowledgment);
            return;
        }
        JsonNode cdcRecord = unwrapCdcRecord(parseCdcRecord(message));
        if (!"product".equalsIgnoreCase(cdcRecord.path("aggregate_type").asText())
                || !"PENDING".equalsIgnoreCase(cdcRecord.path("status").asText())) {
            acknowledge(acknowledgment);
            return;
        }
        String eventType = cdcRecord.path("type").asText(null);
        if (!"PRODUCT_SEARCH_UPSERTED".equalsIgnoreCase(eventType)
                && !"PRODUCT_SEARCH_REMOVED".equalsIgnoreCase(eventType)) {
            acknowledge(acknowledgment);
            return;
        }
        String rawPayload = cdcRecord.path("payload").asText(null);
        if (!StringUtils.hasText(rawPayload)) {
            throw new NonRetryableKafkaConsumerException("empty outbox payload");
        }
        EventEnvelope envelope = parseEnvelope(rawPayload, eventType);
        KafkaMessageMetadata metadata = new KafkaMessageMetadata(record.topic(), record.partition(), record.offset(), consumerGroup);
        if ("PRODUCT_SEARCH_UPSERTED".equalsIgnoreCase(eventType)) {
            indexingService.handleUpsert(envelope.eventId(), parseUpsertPayload(envelope), metadata);
        } else {
            indexingService.handleRemove(envelope.eventId(), parseRemovedPayload(envelope), metadata);
        }
        acknowledge(acknowledgment);
    }

    private JsonNode parseCdcRecord(String rawMessage) {
        try {
            return objectMapper.readTree(rawMessage);
        } catch (Exception ex) {
            throw new NonRetryableKafkaConsumerException("invalid product search outbox cdc record", ex);
        }
    }

    private JsonNode unwrapCdcRecord(JsonNode root) {
        if (root == null) {
            return MissingNode.getInstance();
        }
        JsonNode payload = root.path("payload");
        if (root.has("schema") && payload.isObject()) {
            return payload;
        }
        return root;
    }

    private EventEnvelope parseEnvelope(String rawPayload, String eventType) {
        try {
            EventEnvelope envelope = objectMapper.readValue(rawPayload, EventEnvelope.class);
            if (envelope == null || envelope.payload() == null || !eventType.equalsIgnoreCase(envelope.type())) {
                throw new NonRetryableKafkaConsumerException("invalid event envelope");
            }
            return envelope;
        } catch (NonRetryableKafkaConsumerException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NonRetryableKafkaConsumerException("invalid event envelope", ex);
        }
    }

    private ProductSearchUpsertedPayload parseUpsertPayload(EventEnvelope envelope) {
        try {
            return objectMapper.treeToValue(envelope.payload(), ProductSearchUpsertedPayload.class);
        } catch (Exception ex) {
            throw new NonRetryableKafkaConsumerException("invalid event payload", ex);
        }
    }

    private ProductSearchRemovedPayload parseRemovedPayload(EventEnvelope envelope) {
        try {
            return objectMapper.treeToValue(envelope.payload(), ProductSearchRemovedPayload.class);
        } catch (Exception ex) {
            throw new NonRetryableKafkaConsumerException("invalid event payload", ex);
        }
    }

    private void acknowledge(Acknowledgment acknowledgment) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}
