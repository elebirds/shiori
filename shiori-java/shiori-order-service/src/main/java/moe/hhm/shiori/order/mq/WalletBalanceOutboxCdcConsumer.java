package moe.hhm.shiori.order.mq;

import moe.hhm.shiori.order.event.EventEnvelope;
import moe.hhm.shiori.order.event.WalletBalanceChangedPayload;
import moe.hhm.shiori.order.service.OrderMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "order.kafka", name = "enabled", havingValue = "true")
public class WalletBalanceOutboxCdcConsumer {

    private static final Logger log = LoggerFactory.getLogger(WalletBalanceOutboxCdcConsumer.class);
    private static final String CONSUMER_NAME = "wallet_balance_outbox";

    private final ObjectMapper objectMapper;
    private final WalletBalanceOutboxCdcConsumeService consumeService;
    private final OrderMetrics orderMetrics;
    private final String consumerGroup;

    public WalletBalanceOutboxCdcConsumer(ObjectMapper objectMapper,
                                          WalletBalanceOutboxCdcConsumeService consumeService,
                                          OrderMetrics orderMetrics,
                                          @Value("${order.kafka.wallet-balance-outbox-group-id:shiori-order-wallet-cdc}")
                                          String consumerGroup) {
        this.objectMapper = objectMapper;
        this.consumeService = consumeService;
        this.orderMetrics = orderMetrics;
        this.consumerGroup = consumerGroup;
    }

    @KafkaListener(
            topics = "${order.kafka.wallet-balance-outbox-topic:shiori.cdc.payment.outbox.raw}",
            groupId = "${order.kafka.wallet-balance-outbox-group-id:shiori-order-wallet-cdc}"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String message = record == null ? null : record.value();
        if (!StringUtils.hasText(message)) {
            acknowledge(acknowledgment);
            return;
        }
        if (record != null && record.timestamp() > 0) {
            double lagSeconds = Math.max((System.currentTimeMillis() - record.timestamp()) / 1000.0d, 0.0d);
            orderMetrics.recordKafkaConsumerLagSeconds(CONSUMER_NAME, lagSeconds);
        }
        WalletBalanceEvent walletBalanceEvent = parseMessage(message);
        if (walletBalanceEvent == null) {
            acknowledge(acknowledgment);
            return;
        }
        consumeService.handle(
                walletBalanceEvent.eventId(),
                walletBalanceEvent.payload(),
                new KafkaMessageMetadata(record.topic(), record.partition(), record.offset(), consumerGroup)
        );
        acknowledge(acknowledgment);
    }

    private WalletBalanceEvent parseMessage(String message) {
        JsonNode cdcRecord;
        try {
            cdcRecord = objectMapper.readTree(message);
        } catch (JacksonException ex) {
            log.warn("非法 WalletBalanceChanged CDC 记录将进入 DLT: {}", ex.getMessage());
            throw new NonRetryableKafkaConsumerException("invalid wallet balance cdc record", ex);
        }
        if (!"wallet".equalsIgnoreCase(cdcRecord.path("aggregate_type").asText())
                || !"PENDING".equalsIgnoreCase(cdcRecord.path("status").asText())
                || !"WalletBalanceChanged".equals(cdcRecord.path("type").asText())) {
            return null;
        }
        String rawPayload = cdcRecord.path("payload").asText(null);
        if (!StringUtils.hasText(rawPayload)) {
            throw new NonRetryableKafkaConsumerException("wallet balance cdc payload is empty");
        }
        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(rawPayload, EventEnvelope.class);
        } catch (JacksonException ex) {
            log.warn("非法 WalletBalanceChanged 事件将进入 DLT: {}", ex.getMessage());
            throw new NonRetryableKafkaConsumerException("invalid wallet balance event envelope", ex);
        }
        if (envelope == null || !"WalletBalanceChanged".equals(envelope.type()) || envelope.payload() == null) {
            throw new NonRetryableKafkaConsumerException("invalid wallet balance event envelope");
        }
        WalletBalanceChangedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), WalletBalanceChangedPayload.class);
        } catch (JacksonException ex) {
            log.warn("无效 WalletBalanceChanged payload 将进入 DLT: {}", ex.getMessage());
            throw new NonRetryableKafkaConsumerException("invalid wallet balance event payload", ex);
        }
        if (payload == null || payload.userId() == null || payload.userId() <= 0) {
            throw new NonRetryableKafkaConsumerException("wallet balance event userId is invalid");
        }
        if (!StringUtils.hasText(envelope.eventId())) {
            throw new NonRetryableKafkaConsumerException("wallet balance eventId is missing");
        }
        return new WalletBalanceEvent(envelope.eventId(), payload);
    }

    private void acknowledge(Acknowledgment acknowledgment) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }

    private record WalletBalanceEvent(String eventId, WalletBalanceChangedPayload payload) {
    }
}
