package moe.hhm.shiori.order.mq;

import moe.hhm.shiori.order.event.EventEnvelope;
import moe.hhm.shiori.order.event.WalletBalanceChangedPayload;
import moe.hhm.shiori.order.service.OrderMetrics;
import moe.hhm.shiori.order.service.OrderRefundService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "order.kafka", name = "enabled", havingValue = "true")
public class WalletBalanceOutboxCdcConsumer {

    private static final Logger log = LoggerFactory.getLogger(WalletBalanceOutboxCdcConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderMetrics orderMetrics;
    private final OrderRefundService orderRefundService;

    public WalletBalanceOutboxCdcConsumer(ObjectMapper objectMapper,
                                          OrderMetrics orderMetrics,
                                          OrderRefundService orderRefundService) {
        this.objectMapper = objectMapper;
        this.orderMetrics = orderMetrics;
        this.orderRefundService = orderRefundService;
    }

    @KafkaListener(
            topics = "${order.kafka.wallet-balance-outbox-topic:shiori.cdc.payment.outbox.raw}",
            groupId = "${order.kafka.wallet-balance-outbox-group-id:shiori-order-wallet-cdc}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String message = record == null ? null : record.value();
        if (!StringUtils.hasText(message)) {
            return;
        }
        if (record != null && record.timestamp() > 0) {
            double lagSeconds = Math.max((System.currentTimeMillis() - record.timestamp()) / 1000.0d, 0.0d);
            orderMetrics.recordKafkaConsumerLagSeconds("wallet_balance_outbox", lagSeconds);
        }
        JsonNode cdcRecord;
        try {
            cdcRecord = objectMapper.readTree(message);
        } catch (JacksonException ex) {
            log.warn("忽略非法 WalletBalanceChanged CDC 记录: {}", ex.getMessage());
            return;
        }
        if (!"wallet".equalsIgnoreCase(cdcRecord.path("aggregate_type").asText())
                || !"PENDING".equalsIgnoreCase(cdcRecord.path("status").asText())
                || !"WalletBalanceChanged".equals(cdcRecord.path("type").asText())) {
            return;
        }
        String rawPayload = cdcRecord.path("payload").asText(null);
        if (!StringUtils.hasText(rawPayload)) {
            return;
        }
        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(rawPayload, EventEnvelope.class);
        } catch (JacksonException ex) {
            log.warn("忽略非法 WalletBalanceChanged 事件: {}", ex.getMessage());
            return;
        }
        if (envelope == null || !"WalletBalanceChanged".equals(envelope.type()) || envelope.payload() == null) {
            return;
        }
        WalletBalanceChangedPayload payload;
        try {
            payload = objectMapper.treeToValue(envelope.payload(), WalletBalanceChangedPayload.class);
        } catch (JacksonException ex) {
            log.warn("忽略无效 WalletBalanceChanged payload: {}", ex.getMessage());
            return;
        }
        if (payload == null || payload.userId() == null || payload.userId() <= 0) {
            return;
        }
        orderRefundService.retryPendingRefundsBySeller(payload.userId());
    }
}
