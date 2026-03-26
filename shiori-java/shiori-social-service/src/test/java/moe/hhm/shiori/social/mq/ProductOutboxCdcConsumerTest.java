package moe.hhm.shiori.social.mq;

import moe.hhm.shiori.social.event.ProductPublishedPayload;
import moe.hhm.shiori.social.service.SocialPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProductOutboxCdcConsumerTest {

    @Mock
    private SocialPostService socialPostService;

    private ProductOutboxCdcConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ProductOutboxCdcConsumer(new ObjectMapper(), socialPostService);
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

        consumer.onMessage(message);

        verify(socialPostService).handleProductPublished("event-1", new ProductPublishedPayload(
                1001L,
                "P001",
                2002L,
                "Java Book",
                "product/2002/202603/a.jpg",
                3900L,
                4900L,
                "main"
        ));
    }

    @Test
    void shouldRejectWhenAggregateTypeInvalid() {
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

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class);
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

        consumer.onMessage(message);

        verifyNoInteractions(socialPostService);
    }
}
