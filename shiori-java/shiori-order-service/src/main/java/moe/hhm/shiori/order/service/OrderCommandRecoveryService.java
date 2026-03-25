package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.domain.OrderCommandType;
import moe.hhm.shiori.order.model.OrderCommandRecord;
import moe.hhm.shiori.order.repository.OrderCommandMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "order.command", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderCommandRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandRecoveryService.class);

    private final OrderCommandMapper orderCommandMapper;
    private final OrderCreateWorkflowService orderCreateWorkflowService;
    private final OrderPayWorkflowService orderPayWorkflowService;
    private final OrderConfirmSettlementWorkflowService orderConfirmSettlementWorkflowService;
    private final OrderProperties orderProperties;
    private final OrderMetrics orderMetrics;

    public OrderCommandRecoveryService(OrderCommandMapper orderCommandMapper,
                                       OrderCreateWorkflowService orderCreateWorkflowService,
                                       OrderPayWorkflowService orderPayWorkflowService,
                                       OrderConfirmSettlementWorkflowService orderConfirmSettlementWorkflowService,
                                       OrderProperties orderProperties,
                                       OrderMetrics orderMetrics) {
        this.orderCommandMapper = orderCommandMapper;
        this.orderCreateWorkflowService = orderCreateWorkflowService;
        this.orderPayWorkflowService = orderPayWorkflowService;
        this.orderConfirmSettlementWorkflowService = orderConfirmSettlementWorkflowService;
        this.orderProperties = orderProperties;
        this.orderMetrics = orderMetrics;
    }

    @Scheduled(fixedDelayString = "${order.command.recovery-fixed-delay-ms:3000}")
    public void recoverDueCommands() {
        int batchSize = Math.max(orderProperties.getCommand().getRecoveryBatchSize(), 1);
        int stalePreparedSeconds = Math.max(orderProperties.getCommand().getStalePreparedSeconds(), 1);
        List<OrderCommandRecord> candidates = orderCommandMapper.listRecoveryCandidates(
                LocalDateTime.now().minusSeconds(stalePreparedSeconds),
                batchSize
        );
        for (OrderCommandRecord candidate : candidates) {
            try {
                dispatch(candidate);
                orderMetrics.incOrderCommandRecovery(candidate.commandType(), "success");
            } catch (RuntimeException ex) {
                int nextRetryCount = nextRetryCount(candidate.retryCount());
                orderCommandMapper.scheduleRetry(
                        candidate.id(),
                        nextRetryCount,
                        trimError(ex),
                        LocalDateTime.now().plusSeconds(calcBackoffSeconds(nextRetryCount))
                );
                orderMetrics.incOrderCommandRecovery(candidate.commandType(), "retry");
                log.warn("订单命令恢复失败, commandNo={}, type={}, retryCount={}, err={}",
                        candidate.commandNo(), candidate.commandType(), nextRetryCount, trimError(ex));
            }
        }
    }

    void dispatch(OrderCommandRecord command) {
        OrderCommandType commandType = OrderCommandType.fromCode(command.commandType());
        if (commandType == null) {
            log.warn("忽略未知命令类型, commandNo={}, commandType={}", command.commandNo(), command.commandType());
            return;
        }
        switch (commandType) {
            case CREATE_ORDER -> orderCreateWorkflowService.recover(command);
            case PAY_BALANCE_ORDER -> orderPayWorkflowService.recover(command);
            case CONFIRM_RECEIPT_SETTLEMENT -> orderConfirmSettlementWorkflowService.recover(command);
            default -> log.warn("忽略未支持的命令类型, commandNo={}, commandType={}",
                    command.commandNo(), command.commandType());
        }
    }

    private int nextRetryCount(Integer retryCount) {
        int current = retryCount == null ? 0 : retryCount;
        return current + 1;
    }

    private int calcBackoffSeconds(int retryCount) {
        int max = Math.max(orderProperties.getCommand().getMaxBackoffSeconds(), 1);
        int exponent = Math.min(Math.max(retryCount - 1, 0), 12);
        long value = 1L << exponent;
        return (int) Math.min(value, max);
    }

    private String trimError(Throwable ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "unknown";
        }
        String normalized = ex.getMessage().trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500);
    }
}
