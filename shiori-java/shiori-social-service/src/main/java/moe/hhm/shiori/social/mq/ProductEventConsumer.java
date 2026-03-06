package moe.hhm.shiori.social.mq;

import moe.hhm.shiori.social.event.EventEnvelope;
import moe.hhm.shiori.social.event.ProductPublishedPayload;
import moe.hhm.shiori.social.service.SocialPostService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "social.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProductEventConsumer {

    private final ObjectMapper objectMapper;
    private final SocialPostService socialPostService;

    public ProductEventConsumer(ObjectMapper objectMapper, SocialPostService socialPostService) {
        this.objectMapper = objectMapper;
        this.socialPostService = socialPostService;
    }

    @RabbitListener(queues = "${social.mq.product-published-queue:q.social.product.published}")
    public void onMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        EventEnvelope envelope = parseEnvelope(rawMessage);
        ProductPublishedPayload payload = parsePayload(envelope);
        if (!StringUtils.hasText(envelope.eventId())
                || payload.ownerUserId() == null || payload.ownerUserId() <= 0
                || payload.productId() == null || payload.productId() <= 0) {
            throw new AmqpRejectAndDontRequeueException("invalid product published event");
        }
        socialPostService.handleProductPublished(envelope.eventId(), payload);
    }

    private EventEnvelope parseEnvelope(String rawMessage) {
        try {
            EventEnvelope envelope = objectMapper.readValue(rawMessage, EventEnvelope.class);
            if (envelope == null || envelope.payload() == null) {
                throw new AmqpRejectAndDontRequeueException("empty event envelope");
            }
            if (!"PRODUCT_PUBLISHED".equalsIgnoreCase(envelope.type())) {
                throw new AmqpRejectAndDontRequeueException("unsupported event type: " + envelope.type());
            }
            return envelope;
        } catch (AmqpRejectAndDontRequeueException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AmqpRejectAndDontRequeueException("invalid event envelope", ex);
        }
    }

    private ProductPublishedPayload parsePayload(EventEnvelope envelope) {
        try {
            return objectMapper.treeToValue(envelope.payload(), ProductPublishedPayload.class);
        } catch (Exception ex) {
            throw new AmqpRejectAndDontRequeueException("invalid event payload", ex);
        }
    }
}
