package moe.hhm.shiori.social.mq;

import moe.hhm.shiori.social.event.ProductPublishedPayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ProductOutboxCdcConsumerTest {

    @Mock
    private ProductOutboxCdcConsumeService consumeService;
    @Mock
    private Acknowledgment acknowledgment;

    private ProductOutboxCdcConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ProductOutboxCdcConsumer(new ObjectMapper(), consumeService, "shiori-social-product-cdc");
    }

    @Test
    void shouldHandleValidProductOutboxCdcMessage() {
        String message = """
                {
                  "event_id":"event-1",
                  "aggregate_type":"product",
                  "aggregate_id":"P001",
                  "message_key":"P001",
                  "type":"PRODUCT_PUBLISHED",
                  "payload":"{\\"eventId\\":\\"event-1\\",\\"type\\":\\"PRODUCT_PUBLISHED\\",\\"aggregateId\\":\\"P001\\",\\"createdAt\\":\\"2026-03-07T00:00:00Z\\",\\"payload\\":{\\"productId\\":1001,\\"productNo\\":\\"P001\\",\\"ownerUserId\\":2002,\\"title\\":\\"Java Book\\",\\"coverObjectKey\\":\\"product/2002/202603/a.jpg\\",\\"minPriceCent\\":3900,\\"maxPriceCent\\":4900,\\"campusCode\\":\\"main\\"}}",
                  "status":"PENDING"
                }
                """;

        consumer.onMessage(new ConsumerRecord<>("shiori.cdc.product.outbox.raw", 1, 8L, "P001", message), acknowledgment);

        verify(consumeService).handle(eq("event-1"), eq(new ProductPublishedPayload(
                1001L,
                "P001",
                2002L,
                "Java Book",
                "product/2002/202603/a.jpg",
                3900L,
                4900L,
                "main"
        )), argThat(metadata -> metadata != null
                && "shiori.cdc.product.outbox.raw".equals(metadata.topic())
                && metadata.partition() == 1
                && metadata.offset() == 8L
                && "shiori-social-product-cdc".equals(metadata.consumerGroup())));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldIgnoreWhenAggregateTypeInvalid() {
        String message = """
                {
                  "event_id":"event-2",
                  "aggregate_type":"user",
                  "aggregate_id":"P002",
                  "message_key":"P002",
                  "type":"PRODUCT_PUBLISHED",
                  "payload":"{}",
                  "status":"PENDING"
                }
                """;

        consumer.onMessage(
                new ConsumerRecord<String, String>("shiori.cdc.product.outbox.raw", 0, 1L, "P002", message),
                acknowledgment
        );

        verifyNoInteractions(consumeService);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldIgnoreWhenOutboxStatusNotPending() {
        String message = """
                {
                  "event_id":"event-3",
                  "aggregate_type":"product",
                  "aggregate_id":"P003",
                  "message_key":"P003",
                  "type":"PRODUCT_PUBLISHED",
                  "payload":"{\\"eventId\\":\\"event-3\\",\\"type\\":\\"PRODUCT_PUBLISHED\\",\\"aggregateId\\":\\"P003\\",\\"createdAt\\":\\"2026-03-07T00:00:00Z\\",\\"payload\\":{\\"productId\\":1003,\\"productNo\\":\\"P003\\",\\"ownerUserId\\":2002,\\"title\\":\\"Java Book\\",\\"coverObjectKey\\":\\"product/2002/202603/a.jpg\\",\\"minPriceCent\\":3900,\\"maxPriceCent\\":4900,\\"campusCode\\":\\"main\\"}}",
                  "status":"SENT"
                }
                """;

        consumer.onMessage(
                new ConsumerRecord<String, String>("shiori.cdc.product.outbox.raw", 2, 5L, "P003", message),
                acknowledgment
        );

        verifyNoInteractions(consumeService);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldThrowWithoutAcknowledgingWhenPayloadInvalid() {
        String message = """
                {
                  "event_id":"event-4",
                  "aggregate_type":"product",
                  "aggregate_id":"P004",
                  "message_key":"P004",
                  "type":"PRODUCT_PUBLISHED",
                  "payload":"not-json",
                  "status":"PENDING"
                }
                """;

        assertThatThrownBy(() -> consumer.onMessage(
                new ConsumerRecord<String, String>("shiori.cdc.product.outbox.raw", 0, 2L, "P004", message),
                acknowledgment))
                .isInstanceOf(NonRetryableKafkaConsumerException.class);

        verifyNoInteractions(consumeService);
        verifyNoMoreInteractions(acknowledgment);
    }
}
