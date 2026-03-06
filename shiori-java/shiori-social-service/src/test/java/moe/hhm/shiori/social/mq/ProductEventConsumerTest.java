package moe.hhm.shiori.social.mq;

import moe.hhm.shiori.social.service.SocialPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductEventConsumerTest {

    @Mock
    private SocialPostService socialPostService;

    private ProductEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ProductEventConsumer(new ObjectMapper(), socialPostService);
    }

    @Test
    void shouldHandleValidProductPublishedEvent() {
        String message = """
                {
                  "eventId":"event-1",
                  "type":"PRODUCT_PUBLISHED",
                  "aggregateId":"P001",
                  "createdAt":"2026-03-07T00:00:00Z",
                  "payload":{
                    "productId":1001,
                    "productNo":"P001",
                    "ownerUserId":2002,
                    "title":"Java Book",
                    "coverObjectKey":"product/2002/202603/a.jpg",
                    "minPriceCent":3900,
                    "maxPriceCent":4900,
                    "campusCode":"main"
                  }
                }
                """;

        consumer.onMessage(message);

        verify(socialPostService).handleProductPublished("event-1", new moe.hhm.shiori.social.event.ProductPublishedPayload(
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
    void shouldRejectWhenEnvelopeInvalid() {
        assertThatThrownBy(() -> consumer.onMessage("{invalid-json"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void shouldRejectWhenPayloadMissingOwner() {
        String message = """
                {
                  "eventId":"event-2",
                  "type":"PRODUCT_PUBLISHED",
                  "aggregateId":"P002",
                  "createdAt":"2026-03-07T00:00:00Z",
                  "payload":{
                    "productId":1002,
                    "productNo":"P002"
                  }
                }
                """;

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
