package moe.hhm.shiori.order.mq;

import java.sql.SQLNonTransientException;
import moe.hhm.shiori.order.event.WalletBalanceChangedPayload;
import moe.hhm.shiori.order.service.OrderRefundService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.NonTransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(rollbackFor = Exception.class)
    public void handle(String eventId, WalletBalanceChangedPayload payload, KafkaMessageMetadata metadata) {
        try {
            if (!kafkaConsumeLogService.startProcessing(eventId, "WalletBalanceChanged", metadata)) {
                return;
            }
            orderRefundService.retryPendingRefundsBySellerOrThrow(payload.userId());
            kafkaConsumeLogService.markSucceeded(eventId, metadata);
        } catch (KafkaProcessingInProgressException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            kafkaConsumeLogService.markFailed(eventId, metadata, ex);
            throw classify(ex);
        }
    }

    private RuntimeException classify(RuntimeException ex) {
        if (ex instanceof NonRetryableKafkaConsumerException nonRetryable) {
            return nonRetryable;
        }
        if (!isNonRetryable(ex)) {
            return ex;
        }
        return new NonRetryableKafkaConsumerException("wallet balance outbox consume failed without retry", ex);
    }

    private boolean isNonRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NonTransientDataAccessException
                    || current instanceof SQLNonTransientException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
