package moe.hhm.shiori.order.mq;

import moe.hhm.shiori.order.event.WalletBalanceChangedPayload;
import moe.hhm.shiori.order.service.OrderRefundService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "order.kafka", name = "enabled", havingValue = "true")
public class WalletBalanceOutboxCdcConsumeService {

    private final KafkaConsumeLogService kafkaConsumeLogService;
    private final OrderRefundService orderRefundService;

    public WalletBalanceOutboxCdcConsumeService(KafkaConsumeLogService kafkaConsumeLogService,
                                                OrderRefundService orderRefundService) {
        this.kafkaConsumeLogService = kafkaConsumeLogService;
        this.orderRefundService = orderRefundService;
    }

    public void handle(String eventId, WalletBalanceChangedPayload payload, KafkaMessageMetadata metadata) {
        if (!kafkaConsumeLogService.startProcessing(eventId, "WalletBalanceChanged", metadata)) {
            return;
        }
        try {
            orderRefundService.retryPendingRefundsBySellerOrThrow(payload.userId());
            kafkaConsumeLogService.markSucceeded(eventId, metadata);
        } catch (RuntimeException ex) {
            kafkaConsumeLogService.markFailed(eventId, metadata, ex);
            throw ex;
        }
    }
}
