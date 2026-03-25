package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import moe.hhm.shiori.order.client.SettleBalancePaymentSnapshot;
import moe.hhm.shiori.order.model.OrderCommandEntity;
import moe.hhm.shiori.order.model.OrderCommandRecord;
import moe.hhm.shiori.order.repository.OrderCommandMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderConfirmSettlementWorkflowService {

    private static final String COMMAND_TYPE = "CONFIRM_RECEIPT_SETTLEMENT";
    private static final List<String> DEFAULT_ROLES = List.of("ROLE_USER");
    private static final String SUCCESS_MESSAGE = "SUCCESS";

    private final OrderCommandService orderCommandService;
    private final OrderCommandMapper orderCommandMapper;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;

    @Autowired
    public OrderConfirmSettlementWorkflowService(OrderCommandService orderCommandService,
                                                 OrderCommandMapper orderCommandMapper,
                                                 ObjectMapper objectMapper,
                                                 PlatformTransactionManager transactionManager) {
        this(orderCommandService, orderCommandMapper, objectMapper, new TransactionTemplate(transactionManager));
    }

    OrderConfirmSettlementWorkflowService(OrderCommandService orderCommandService,
                                          OrderCommandMapper orderCommandMapper,
                                          ObjectMapper objectMapper,
                                          TransactionOperations transactionOperations) {
        this.orderCommandService = orderCommandService;
        this.orderCommandMapper = orderCommandMapper;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
    }

    void prepare(Long buyerUserId, String orderNo) {
        transactionOperations.executeWithoutResult(status -> {
            OrderCommandRecord activeCommand = orderCommandMapper.findActiveByOrderNoAndType(orderNo, COMMAND_TYPE);
            if (activeCommand != null) {
                return;
            }
            OrderCommandRecord existing = orderCommandMapper.findByOperatorAndTypeAndIdempotencyKey(
                    buyerUserId,
                    COMMAND_TYPE,
                    orderNo
            );
            if (existing != null) {
                return;
            }

            OrderCommandEntity entity = new OrderCommandEntity();
            entity.setCommandNo("cmd-" + UUID.randomUUID());
            entity.setCommandType(COMMAND_TYPE);
            entity.setOperatorUserId(buyerUserId);
            entity.setIdempotencyKey(orderNo);
            entity.setOrderNo(orderNo);
            entity.setStatus("PREPARED");
            entity.setRequestPayload(writeValue(new ConfirmSettlementRequestPayload(buyerUserId, orderNo)));
            entity.setProgressPayload(writeValue(new ConfirmSettlementProgressPayload(null, null)));
            entity.setRetryCount(0);

            try {
                orderCommandMapper.insertOrderCommand(entity);
                orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "PREPARED");
            } catch (DuplicateKeyException ignored) {
            }
        });
    }

    public void recover(OrderCommandRecord command) {
        switch (command.status()) {
            case "PREPARED" -> recoverPreparedCommand(command);
            case "REMOTE_SUCCEEDED" -> completeRecoveredCommand(command.id());
            default -> {
            }
        }
    }

    private void recoverPreparedCommand(OrderCommandRecord command) {
        ConfirmSettlementRequestPayload requestPayload =
                readValue(command.requestPayload(), ConfirmSettlementRequestPayload.class);
        SettleBalancePaymentSnapshot settled = orderCommandService.paymentServiceClient().settleOrderPayment(
                requestPayload.orderNo(),
                OrderCommandService.SOURCE_BUYER,
                requestPayload.buyerUserId(),
                requestPayload.buyerUserId(),
                DEFAULT_ROLES
        );
        ConfirmSettlementProgressPayload progressPayload =
                new ConfirmSettlementProgressPayload(settled.paymentNo(), settled.status());
        transactionOperations.executeWithoutResult(status ->
                orderCommandMapper.markRemoteSucceeded(command.id(), writeValue(progressPayload)));
        orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "REMOTE_SUCCEEDED");
        completeRecoveredCommand(command.id());
    }

    private void completeRecoveredCommand(Long commandId) {
        transactionOperations.executeWithoutResult(status ->
                orderCommandMapper.markCompleted(commandId, 0, SUCCESS_MESSAGE));
        orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "COMPLETED");
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("序列化确认收货结算命令失败", ex);
        }
    }

    private <T> T readValue(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException ex) {
            throw new IllegalStateException("反序列化确认收货结算命令失败", ex);
        }
    }

    private record ConfirmSettlementRequestPayload(Long buyerUserId, String orderNo) {
    }

    private record ConfirmSettlementProgressPayload(String paymentNo, String settleStatus) {
    }
}
