package moe.hhm.shiori.search.mq;

import moe.hhm.shiori.search.event.ProductSearchUpsertedPayload;
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
class ProductSearchOutboxCdcConsumerTest {

    @Mock
    private ProductSearchIndexingService indexingService;
    @Mock
    private Acknowledgment acknowledgment;

    private ProductSearchOutboxCdcConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ProductSearchOutboxCdcConsumer(new ObjectMapper(), indexingService, "shiori-search-indexer");
    }

    @Test
    void shouldHandleValidSearchUpsertEvent() {
        String message = """
                {
                  "event_id":"event-1",
                  "aggregate_type":"product",
                  "aggregate_id":"P001",
                  "message_key":"P001",
                  "type":"PRODUCT_SEARCH_UPSERTED",
                  "payload":"{\\"eventId\\":\\"event-1\\",\\"type\\":\\"PRODUCT_SEARCH_UPSERTED\\",\\"aggregateId\\":\\"P001\\",\\"createdAt\\":\\"2026-03-27T00:00:00Z\\",\\"payload\\":{\\"productId\\":1001,\\"productNo\\":\\"P001\\",\\"ownerUserId\\":2002,\\"title\\":\\"Java Book\\",\\"description\\":\\"desc\\",\\"coverObjectKey\\":\\"product/2002/202603/a.jpg\\",\\"categoryCode\\":\\"TEXTBOOK\\",\\"subCategoryCode\\":\\"TEXTBOOK_UNSPEC\\",\\"conditionLevel\\":\\"GOOD\\",\\"tradeMode\\":\\"MEETUP\\",\\"campusCode\\":\\"main\\",\\"minPriceCent\\":3900,\\"maxPriceCent\\":4900,\\"totalStock\\":8,\\"status\\":2,\\"version\\":9,\\"createdAt\\":\\"2026-03-27T00:00:00\\",\\"occurredAt\\":\\"2026-03-27T00:01:00\\"}}",
                  "status":"PENDING"
                }
                """;

        consumer.onMessage(new ConsumerRecord<>("shiori.cdc.product.outbox.raw", 1, 8L, "P001", message), acknowledgment);

        verify(indexingService).handleUpsert(eq("event-1"), eq(new ProductSearchUpsertedPayload(
                1001L,
                "P001",
                2002L,
                "Java Book",
                "desc",
                "product/2002/202603/a.jpg",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main",
                3900L,
                4900L,
                8,
                2,
                9L,
                "2026-03-27T00:00:00",
                "2026-03-27T00:01:00"
        )), argThat(metadata -> metadata != null
                && "shiori.cdc.product.outbox.raw".equals(metadata.topic())
                && metadata.partition() == 1
                && metadata.offset() == 8L
                && "shiori-search-indexer".equals(metadata.consumerGroup())));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldIgnoreUnsupportedEventType() {
        String message = """
                {
                  "event_id":"event-2",
                  "aggregate_type":"product",
                  "aggregate_id":"P002",
                  "message_key":"P002",
                  "type":"PRODUCT_PUBLISHED",
                  "payload":"{}",
                  "status":"PENDING"
                }
                """;

        consumer.onMessage(new ConsumerRecord<>("shiori.cdc.product.outbox.raw", 0, 1L, "P002", message), acknowledgment);

        verifyNoInteractions(indexingService);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldThrowWithoutAcknowledgingWhenPayloadInvalid() {
        String message = """
                {
                  "event_id":"event-3",
                  "aggregate_type":"product",
                  "aggregate_id":"P003",
                  "message_key":"P003",
                  "type":"PRODUCT_SEARCH_UPSERTED",
                  "payload":"not-json",
                  "status":"PENDING"
                }
                """;

        assertThatThrownBy(() -> consumer.onMessage(
                new ConsumerRecord<>("shiori.cdc.product.outbox.raw", 0, 2L, "P003", message),
                acknowledgment))
                .isInstanceOf(NonRetryableKafkaConsumerException.class);

        verifyNoInteractions(indexingService);
        verifyNoMoreInteractions(acknowledgment);
    }

    @Test
    void shouldHandleDebeziumWrappedSearchUpsertEvent() {
        String message = """
                {
                  "schema":{"type":"struct"},
                  "payload":{
                    "event_id":"event-4",
                    "aggregate_type":"product",
                    "aggregate_id":"P001",
                    "message_key":"P001",
                    "type":"PRODUCT_SEARCH_UPSERTED",
                    "payload":"{\\"eventId\\":\\"event-4\\",\\"type\\":\\"PRODUCT_SEARCH_UPSERTED\\",\\"aggregateId\\":\\"P001\\",\\"createdAt\\":\\"2026-03-27T00:00:00Z\\",\\"payload\\":{\\"productId\\":1001,\\"productNo\\":\\"P001\\",\\"ownerUserId\\":2002,\\"title\\":\\"Java Book\\",\\"description\\":\\"desc\\",\\"coverObjectKey\\":\\"product/2002/202603/a.jpg\\",\\"categoryCode\\":\\"TEXTBOOK\\",\\"subCategoryCode\\":\\"TEXTBOOK_UNSPEC\\",\\"conditionLevel\\":\\"GOOD\\",\\"tradeMode\\":\\"MEETUP\\",\\"campusCode\\":\\"main\\",\\"minPriceCent\\":3900,\\"maxPriceCent\\":4900,\\"totalStock\\":8,\\"status\\":2,\\"version\\":9,\\"createdAt\\":\\"2026-03-27T00:00:00\\",\\"occurredAt\\":\\"2026-03-27T00:01:00\\"}}",
                    "status":"PENDING"
                  }
                }
                """;

        consumer.onMessage(new ConsumerRecord<>("shiori.cdc.product.outbox.raw", 1, 9L, "P001", message), acknowledgment);

        verify(indexingService).handleUpsert(eq("event-4"), eq(new ProductSearchUpsertedPayload(
                1001L,
                "P001",
                2002L,
                "Java Book",
                "desc",
                "product/2002/202603/a.jpg",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main",
                3900L,
                4900L,
                8,
                2,
                9L,
                "2026-03-27T00:00:00",
                "2026-03-27T00:01:00"
        )), argThat(metadata -> metadata != null
                && metadata.offset() == 9L
                && "shiori-search-indexer".equals(metadata.consumerGroup())));
        verify(acknowledgment).acknowledge();
    }
}
