package moe.hhm.shiori.product.service;

import java.time.Instant;
import java.util.UUID;
import moe.hhm.shiori.product.config.ProductMqProperties;
import moe.hhm.shiori.product.event.EventEnvelope;
import moe.hhm.shiori.product.event.ProductPublishedPayload;
import moe.hhm.shiori.product.model.ProductV2Record;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnBean(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "product.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProductEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ProductMqProperties productMqProperties;

    public ProductEventPublisher(RabbitTemplate rabbitTemplate,
                                 ObjectMapper objectMapper,
                                 ProductMqProperties productMqProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.productMqProperties = productMqProperties;
    }

    public void publishProductPublished(ProductV2Record product) {
        if (!productMqProperties.isEnabled()) {
            return;
        }
        if (product == null || product.id() == null || product.ownerUserId() == null) {
            return;
        }
        ProductPublishedPayload payload = new ProductPublishedPayload(
                product.id(),
                product.productNo(),
                product.ownerUserId(),
                product.title(),
                product.coverObjectKey(),
                product.minPriceCent(),
                product.maxPriceCent(),
                product.campusCode()
        );
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString().replace("-", ""),
                "PRODUCT_PUBLISHED",
                product.productNo(),
                Instant.now().toString(),
                objectMapper.valueToTree(payload)
        );
        publishEnvelope(
                productMqProperties.getEventExchange(),
                productMqProperties.getProductPublishedRoutingKey(),
                writeEnvelope(envelope)
        );
    }

    public void publishEnvelope(String exchangeName, String routingKey, String payload) {
        if (!productMqProperties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(exchangeName) || !StringUtils.hasText(routingKey) || !StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("exchangeName/routingKey/payload 不能为空");
        }
        rabbitTemplate.convertAndSend(exchangeName, routingKey, payload);
    }

    private String writeEnvelope(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException("构建商品事件消息失败", ex);
        }
    }
}
