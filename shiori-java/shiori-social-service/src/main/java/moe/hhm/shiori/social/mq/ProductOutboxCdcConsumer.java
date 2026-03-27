package moe.hhm.shiori.social.mq;

import moe.hhm.shiori.social.event.EventEnvelope;
import moe.hhm.shiori.social.event.ProductPublishedPayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "social.kafka", name = "enabled", havingValue = "true")
public class ProductOutboxCdcConsumer {

    private final ObjectMapper objectMapper;
    private final ProductOutboxCdcConsumeService consumeService;
    private final String consumerGroup;

    public ProductOutboxCdcConsumer(ObjectMapper objectMapper,
                                    ProductOutboxCdcConsumeService consumeService,
                                    @Value("${social.kafka.product-outbox-group-id:shiori-social-product-cdc}")
                                    String consumerGroup) {
        this.objectMapper = objectMapper;
        this.consumeService = consumeService;
        this.consumerGroup = consumerGroup;
    }

    @KafkaListener(
            topics = "${social.kafka.product-outbox-topic:shiori.cdc.product.outbox.raw}",
            groupId = "${social.kafka.product-outbox-group-id:shiori-social-product-cdc}"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String rawMessage = record == null ? null : record.value();
        if (!StringUtils.hasText(rawMessage)) {
            acknowledge(acknowledgment);
            return;
        }
        JsonNode cdcRecord = parseCdcRecord(rawMessage);
        if (!"PENDING".equalsIgnoreCase(cdcRecord.path("status").asText())) {
            acknowledge(acknowledgment);
            return;
        }
        if (!isTargetRecord(cdcRecord)) {
            acknowledge(acknowledgment);
            return;
        }

        EventEnvelope envelope = parseEnvelope(cdcRecord.path("payload").asText());
        ProductPublishedPayload payload = parsePayload(envelope);
        if (!StringUtils.hasText(envelope.eventId())
                || payload.ownerUserId() == null || payload.ownerUserId() <= 0
                || payload.productId() == null || payload.productId() <= 0) {
            throw new NonRetryableKafkaConsumerException("invalid product published event");
        }
        consumeService.handle(
                envelope.eventId(),
                payload,
                new KafkaMessageMetadata(record.topic(), record.partition(), record.offset(), consumerGroup)
        );
        acknowledge(acknowledgment);
    }

    private JsonNode parseCdcRecord(String rawMessage) {
        try {
            return objectMapper.readTree(rawMessage);
        } catch (Exception ex) {
            throw new NonRetryableKafkaConsumerException("invalid product outbox cdc record", ex);
        }
    }

    private boolean isTargetRecord(JsonNode cdcRecord) {
        String aggregateType = cdcRecord.path("aggregate_type").asText(null);
        String type = cdcRecord.path("type").asText(null);
        String payload = cdcRecord.path("payload").asText(null);
        if (!"product".equalsIgnoreCase(aggregateType)) {
            return false;
        }
        if (!"PRODUCT_PUBLISHED".equalsIgnoreCase(type)) {
            return false;
        }
        if (!StringUtils.hasText(payload)) {
            throw new NonRetryableKafkaConsumerException("empty outbox payload");
        }
        return true;
    }

    private EventEnvelope parseEnvelope(String rawPayload) {
        try {
            EventEnvelope envelope = objectMapper.readValue(rawPayload, EventEnvelope.class);
            if (envelope == null || envelope.payload() == null) {
                throw new NonRetryableKafkaConsumerException("empty event envelope");
            }
            if (!"PRODUCT_PUBLISHED".equalsIgnoreCase(envelope.type())) {
                throw new NonRetryableKafkaConsumerException("unsupported event type: " + envelope.type());
            }
            return envelope;
        } catch (NonRetryableKafkaConsumerException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NonRetryableKafkaConsumerException("invalid event envelope", ex);
        }
    }

    private ProductPublishedPayload parsePayload(EventEnvelope envelope) {
        try {
            return objectMapper.treeToValue(envelope.payload(), ProductPublishedPayload.class);
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
