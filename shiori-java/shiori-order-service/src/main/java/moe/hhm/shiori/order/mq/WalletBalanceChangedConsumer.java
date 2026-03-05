package moe.hhm.shiori.order.mq;

import moe.hhm.shiori.order.event.EventEnvelope;
import moe.hhm.shiori.order.event.WalletBalanceChangedPayload;
import moe.hhm.shiori.order.service.OrderRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "order.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WalletBalanceChangedConsumer {

    private static final Logger log = LoggerFactory.getLogger(WalletBalanceChangedConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderRefundService orderRefundService;

    public WalletBalanceChangedConsumer(ObjectMapper objectMapper,
                                        OrderRefundService orderRefundService) {
        this.objectMapper = objectMapper;
        this.orderRefundService = orderRefundService;
    }

    @RabbitListener(queues = "${order.mq.wallet-balance-changed-queue:q.order.wallet.balance.changed}")
    public void onWalletBalanceChanged(String message) {
        if (!StringUtils.hasText(message)) {
            return;
        }
        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message, EventEnvelope.class);
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
