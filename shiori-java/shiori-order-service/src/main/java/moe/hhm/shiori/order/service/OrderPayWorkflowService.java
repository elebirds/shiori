package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.client.ReserveBalancePaymentSnapshot;
import moe.hhm.shiori.order.domain.OrderStatus;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.model.OrderCommandEntity;
import moe.hhm.shiori.order.model.OrderCommandRecord;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.repository.OrderCommandMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderPayWorkflowService {

    private static final String COMMAND_TYPE = "PAY_BALANCE_ORDER";
    private static final List<String> DEFAULT_ROLES = List.of("ROLE_USER");
    private static final String SUCCESS_MESSAGE = "SUCCESS";

    private final OrderCommandService orderCommandService;
    private final OrderCommandMapper orderCommandMapper;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;

    @Autowired
    public OrderPayWorkflowService(OrderCommandService orderCommandService,
                                   OrderCommandMapper orderCommandMapper,
                                   ObjectMapper objectMapper,
                                   PlatformTransactionManager transactionManager) {
        this(orderCommandService, orderCommandMapper, objectMapper, new TransactionTemplate(transactionManager));
    }

    OrderPayWorkflowService(OrderCommandService orderCommandService,
                            OrderCommandMapper orderCommandMapper,
                            ObjectMapper objectMapper,
                            TransactionOperations transactionOperations) {
        this.orderCommandService = orderCommandService;
        this.orderCommandMapper = orderCommandMapper;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
    }

    public OrderOperateResponse payOrderByBalance(Long buyerUserId, String orderNo, String idempotencyKey) {
        String normalizedIdempotencyKey = orderCommandService.requireIdempotencyKey(idempotencyKey);
        OrderRecord order = orderCommandService.requireOrder(orderNo);
        orderCommandService.ensureBuyer(order, buyerUserId);

        if (orderCommandService.hasOperateIdempotencyHit(buyerUserId, OrderCommandService.OP_PAY, normalizedIdempotencyKey, orderNo)) {
            OrderStatus hitStatus = OrderStatus.fromCode(order.status());
            if (hitStatus == OrderStatus.PAID && orderCommandService.isBalanceEscrowOrder(orderNo)) {
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        OrderStatus status = OrderStatus.fromCode(order.status());
        if (status == OrderStatus.PAID) {
            if (orderCommandService.isBalanceEscrowOrder(orderNo)) {
                orderCommandService.saveOperateIdempotency(
                        buyerUserId,
                        OrderCommandService.OP_PAY,
                        normalizedIdempotencyKey,
                        orderNo
                );
                return new OrderOperateResponse(orderNo, OrderStatus.PAID.name(), true);
            }
            throw new BizException(OrderErrorCode.ORDER_PAYMENT_CONFLICT, HttpStatus.CONFLICT);
        }
        if (status != OrderStatus.UNPAID) {
            throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        orderCommandService.ensureFulfillmentReadyForPay(order);

        OrderCommandRecord existing = orderCommandMapper.findByOperatorAndTypeAndIdempotencyKey(
                buyerUserId,
                COMMAND_TYPE,
                normalizedIdempotencyKey
        );
        if (existing != null) {
            return resolveExistingCommand(existing);
        }

        PreparedPayCommand prepared = preparePayCommand(order, buyerUserId, normalizedIdempotencyKey);
        if (prepared.existed()) {
            return resolveExistingCommand(prepared.command());
        }

        ReserveBalancePaymentSnapshot reserved;
        try {
            reserved = orderCommandService.paymentServiceClient().reserveOrderPayment(
                    order.orderNo(),
                    order.buyerUserId(),
                    order.sellerUserId(),
                    order.totalAmountCent(),
                    buyerUserId,
                    DEFAULT_ROLES
            );
        } catch (RuntimeException ex) {
            persistReserveFailure(prepared.command(), ex);
            throw ex;
        }

        PayProgressPayload progress = new PayProgressPayload(reserved.paymentNo(), reserved.status());
        transactionOperations.executeWithoutResult(statusTx ->
                orderCommandMapper.markRemoteSucceeded(prepared.command().id(), writeValue(progress)));
        orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "REMOTE_SUCCEEDED");

        try {
            return finalizePay(order, buyerUserId, normalizedIdempotencyKey, prepared.command(), reserved, false);
        } catch (RuntimeException ex) {
            int nextRetryCount = nextRetryCount(prepared.command().retryCount());
            transactionOperations.executeWithoutResult(statusTx ->
                    orderCommandMapper.scheduleRetry(
                            prepared.command().id(),
                            nextRetryCount,
                            trimError(ex),
                            LocalDateTime.now().plusSeconds(calcBackoffSeconds(nextRetryCount))
                    ));
            throw ex;
        }
    }

    public void recover(OrderCommandRecord command) {
        switch (command.status()) {
            case "PREPARED" -> recoverPreparedCommand(command);
            case "REMOTE_SUCCEEDED" -> recoverRemoteSucceededCommand(command);
            case "COMPENSATING" -> compensateRecoveredCommand(command, FailureRecord.from(command));
            default -> {
            }
        }
    }

    private PreparedPayCommand preparePayCommand(OrderRecord order, Long buyerUserId, String idempotencyKey) {
        return transactionOperations.execute(status -> {
            OrderRecord lockedOrder = orderCommandService.orderMapper().findOrderByOrderNoForUpdate(order.orderNo());
            if (lockedOrder == null) {
                throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
            }

            OrderCommandRecord idempotentCommand = orderCommandMapper.findByOperatorAndTypeAndIdempotencyKey(
                    buyerUserId,
                    COMMAND_TYPE,
                    idempotencyKey
            );
            if (idempotentCommand != null) {
                return new PreparedPayCommand(idempotentCommand, true);
            }

            OrderCommandRecord activeCommand = orderCommandMapper.findActiveByOrderNoAndType(order.orderNo(), COMMAND_TYPE);
            if (activeCommand != null) {
                return new PreparedPayCommand(activeCommand, true);
            }

            OrderStatus latestStatus = OrderStatus.fromCode(lockedOrder.status());
            if (latestStatus == OrderStatus.PAID && orderCommandService.isBalanceEscrowOrder(order.orderNo())) {
                OrderCommandEntity completed = new OrderCommandEntity();
                completed.setCommandNo("cmd-" + UUID.randomUUID());
                completed.setCommandType(COMMAND_TYPE);
                completed.setOperatorUserId(buyerUserId);
                completed.setIdempotencyKey(idempotencyKey);
                completed.setOrderNo(order.orderNo());
                completed.setStatus("COMPLETED");
                completed.setRequestPayload(writeValue(new PayRequestPayload(
                        buyerUserId,
                        idempotencyKey,
                        order.orderNo(),
                        order.sellerUserId(),
                        order.totalAmountCent()
                )));
                completed.setProgressPayload(writeValue(new PayProgressPayload(lockedOrder.paymentNo(), "RESERVED")));
                completed.setResultCode(0);
                completed.setResultMessage(SUCCESS_MESSAGE);
                completed.setRetryCount(0);
                orderCommandMapper.insertOrderCommand(completed);
                return new PreparedPayCommand(requireCommand(completed.getCommandNo()), true);
            }
            if (latestStatus != OrderStatus.UNPAID) {
                throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
            }

            OrderCommandEntity entity = new OrderCommandEntity();
            entity.setCommandNo("cmd-" + UUID.randomUUID());
            entity.setCommandType(COMMAND_TYPE);
            entity.setOperatorUserId(buyerUserId);
            entity.setIdempotencyKey(idempotencyKey);
            entity.setOrderNo(order.orderNo());
            entity.setStatus("PREPARED");
            entity.setRequestPayload(writeValue(new PayRequestPayload(
                    buyerUserId,
                    idempotencyKey,
                    order.orderNo(),
                    order.sellerUserId(),
                    order.totalAmountCent()
            )));
            entity.setProgressPayload(writeValue(new PayProgressPayload(null, null)));
            entity.setRetryCount(0);

            try {
                orderCommandMapper.insertOrderCommand(entity);
                orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "PREPARED");
                return new PreparedPayCommand(requireCommand(entity.getCommandNo()), false);
            } catch (DuplicateKeyException ex) {
                OrderCommandRecord duplicated = orderCommandMapper.findByOperatorAndTypeAndIdempotencyKey(
                        buyerUserId,
                        COMMAND_TYPE,
                        idempotencyKey
                );
                if (duplicated == null) {
                    throw ex;
                }
                return new PreparedPayCommand(duplicated, true);
            }
        });
    }

    private void recoverPreparedCommand(OrderCommandRecord command) {
        PayRequestPayload requestPayload = readValue(command.requestPayload(), PayRequestPayload.class);
        ReserveBalancePaymentSnapshot reserved = orderCommandService.paymentServiceClient().reserveOrderPayment(
                requestPayload.orderNo(),
                requestPayload.buyerUserId(),
                requestPayload.sellerUserId(),
                requestPayload.amountCent(),
                requestPayload.buyerUserId(),
                DEFAULT_ROLES
        );
        transactionOperations.executeWithoutResult(status ->
                orderCommandMapper.markRemoteSucceeded(
                        command.id(),
                        writeValue(new PayProgressPayload(reserved.paymentNo(), reserved.status()))
                ));
        orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "REMOTE_SUCCEEDED");
        recoverRemoteSucceededCommand(command);
    }

    private void recoverRemoteSucceededCommand(OrderCommandRecord command) {
        PayRequestPayload requestPayload = readValue(command.requestPayload(), PayRequestPayload.class);
        PayProgressPayload progressPayload = readValue(command.progressPayload(), PayProgressPayload.class);
        OrderRecord latest = orderCommandService.orderMapper().findOrderByOrderNo(command.orderNo());
        if (latest != null
                && OrderStatus.fromCode(latest.status()) == OrderStatus.PAID
                && Objects.equals(latest.paymentNo(), progressPayload.paymentNo())) {
            transactionOperations.executeWithoutResult(status ->
                    orderCommandMapper.markCompleted(command.id(), 0, SUCCESS_MESSAGE));
            orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "COMPLETED");
            return;
        }

        if (latest == null) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        try {
            finalizePay(
                    latest,
                    requestPayload.buyerUserId(),
                    requestPayload.idempotencyKey(),
                    command,
                    new ReserveBalancePaymentSnapshot(
                            requestPayload.orderNo(),
                            progressPayload.paymentNo(),
                            progressPayload.reserveStatus(),
                            true
                    ),
                    true
            );
        } catch (BizException ex) {
            compensateRecoveredCommand(command, FailureRecord.from(ex));
        }
    }

    private void compensateRecoveredCommand(OrderCommandRecord command, FailureRecord fallbackFailure) {
        PayRequestPayload requestPayload = readValue(command.requestPayload(), PayRequestPayload.class);
        orderCommandService.paymentServiceClient().releaseOrderPayment(
                requestPayload.orderNo(),
                "order_pay_recovery_compensate",
                requestPayload.buyerUserId(),
                DEFAULT_ROLES
        );
        FailureRecord failure = command.resultCode() != null || command.resultMessage() != null
                ? FailureRecord.from(command)
                : fallbackFailure;
        transactionOperations.executeWithoutResult(status ->
                orderCommandMapper.markCompensated(
                        command.id(),
                        command.progressPayload(),
                        failure.code(),
                        failure.message()
                ));
        orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "COMPENSATED");
        orderCommandService.orderMetrics().incOrderCommandCompensation(COMMAND_TYPE, "success");
    }

    private OrderOperateResponse finalizePay(OrderRecord order,
                                             Long buyerUserId,
                                             String idempotencyKey,
                                             OrderCommandRecord command,
                                             ReserveBalancePaymentSnapshot reserved,
                                             boolean idempotent) {
        return transactionOperations.execute(status -> {
            try {
                int affected = orderCommandService.orderMapper().markOrderPaidByBalance(
                        order.orderNo(),
                        buyerUserId,
                        reserved.paymentNo(),
                        LocalDateTime.now(),
                        OrderStatus.UNPAID.getCode(),
                        OrderStatus.PAID.getCode()
                );
                if (affected == 0) {
                    OrderRecord latest = orderCommandService.requireOrder(order.orderNo());
                    if (OrderStatus.fromCode(latest.status()) == OrderStatus.PAID
                            && Objects.equals(latest.paymentNo(), reserved.paymentNo())
                            && orderCommandService.isBalanceEscrowOrder(order.orderNo())) {
                        orderCommandService.saveOperateIdempotency(
                                buyerUserId,
                                OrderCommandService.OP_PAY,
                                idempotencyKey,
                                order.orderNo()
                        );
                        orderCommandMapper.markCompleted(command.id(), 0, SUCCESS_MESSAGE);
                        orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "COMPLETED");
                        return new OrderOperateResponse(order.orderNo(), OrderStatus.PAID.name(), true);
                    }
                    throw new BizException(OrderErrorCode.ORDER_STATUS_INVALID, HttpStatus.CONFLICT);
                }
            } catch (DuplicateKeyException ex) {
                OrderRecord paymentOrder = orderCommandService.orderMapper().findOrderByPaymentNo(reserved.paymentNo());
                if (paymentOrder != null
                        && order.orderNo().equals(paymentOrder.orderNo())
                        && orderCommandService.isBalanceEscrowOrder(order.orderNo())) {
                    orderCommandService.saveOperateIdempotency(
                            buyerUserId,
                            OrderCommandService.OP_PAY,
                            idempotencyKey,
                            order.orderNo()
                    );
                    orderCommandMapper.markCompleted(command.id(), 0, SUCCESS_MESSAGE);
                    orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "COMPLETED");
                    return new OrderOperateResponse(order.orderNo(), OrderStatus.PAID.name(), true);
                }
                orderCommandService.orderMetrics().incIdempotency(OrderCommandService.OP_PAY, "conflict");
                throw new BizException(OrderErrorCode.ORDER_PAYMENT_CONFLICT, HttpStatus.CONFLICT);
            }

            orderCommandService.appendOrderPaidOutbox(
                    order.orderNo(),
                    reserved.paymentNo(),
                    order.buyerUserId(),
                    order.sellerUserId(),
                    order.totalAmountCent()
            );
            orderCommandService.orderMetrics().incStateTransition(
                    OrderStatus.UNPAID.name(),
                    OrderStatus.PAID.name(),
                    OrderCommandService.SOURCE_BUYER
            );
            orderCommandService.insertStatusAudit(
                    order.orderNo(),
                    buyerUserId,
                    OrderCommandService.SOURCE_BUYER,
                    OrderStatus.UNPAID,
                    OrderStatus.PAID,
                    "买家余额支付成功, paymentNo=" + reserved.paymentNo()
            );
            orderCommandService.recordTradeStatusCardSentForChatOrder(order, "ORDER_PAID");
            orderCommandService.saveOperateIdempotency(
                    buyerUserId,
                    OrderCommandService.OP_PAY,
                    idempotencyKey,
                    order.orderNo()
            );
            orderCommandMapper.markCompleted(command.id(), 0, SUCCESS_MESSAGE);
            orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "COMPLETED");
            return new OrderOperateResponse(order.orderNo(), OrderStatus.PAID.name(), idempotent);
        });
    }

    private void persistReserveFailure(OrderCommandRecord command, RuntimeException ex) {
        FailureRecord failure = FailureRecord.from(ex);
        if (ex instanceof BizException) {
            transactionOperations.executeWithoutResult(status ->
                    orderCommandMapper.markFailed(command.id(), failure.code(), failure.message(), trimError(ex)));
            orderCommandService.orderMetrics().incOrderCommand(COMMAND_TYPE, "FAILED");
            return;
        }

        int nextRetryCount = nextRetryCount(command.retryCount());
        transactionOperations.executeWithoutResult(status ->
                orderCommandMapper.scheduleRetry(
                        command.id(),
                        nextRetryCount,
                        trimError(ex),
                        LocalDateTime.now().plusSeconds(calcBackoffSeconds(nextRetryCount))
                ));
    }

    private OrderOperateResponse resolveExistingCommand(OrderCommandRecord command) {
        return switch (command.status()) {
            case "COMPLETED" -> new OrderOperateResponse(command.orderNo(), OrderStatus.PAID.name(), true);
            case "PREPARED", "REMOTE_SUCCEEDED", "COMPENSATING" -> {
                orderCommandService.orderMetrics().incOrderCommandProcessing(COMMAND_TYPE);
                throw new BizException(OrderErrorCode.ORDER_REQUEST_PROCESSING, HttpStatus.CONFLICT);
            }
            case "FAILED", "COMPENSATED" -> throw toTerminalFailure(command);
            default -> throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        };
    }

    private BizException toTerminalFailure(OrderCommandRecord command) {
        OrderErrorCode errorCode = OrderErrorCode.fromCode(command.resultCode());
        if (errorCode != null) {
            return new BizException(errorCode, HttpStatus.CONFLICT);
        }
        return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private OrderCommandRecord requireCommand(String commandNo) {
        OrderCommandRecord record = orderCommandMapper.findByCommandNo(commandNo);
        if (record == null) {
            throw new IllegalStateException("创建支付命令后未能读回命令日志");
        }
        return record;
    }

    private int calcBackoffSeconds(int retryCount) {
        int max = Math.max(orderCommandService.orderProperties().getCommand().getMaxBackoffSeconds(), 1);
        int exponent = Math.min(Math.max(retryCount - 1, 0), 12);
        long value = 1L << exponent;
        return (int) Math.min(value, max);
    }

    private int nextRetryCount(Integer retryCount) {
        int current = retryCount == null ? 0 : retryCount;
        return current + 1;
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("序列化订单支付命令失败", ex);
        }
    }

    private <T> T readValue(String value, Class<T> targetType) {
        try {
            return objectMapper.readValue(value, targetType);
        } catch (JacksonException ex) {
            throw new IllegalStateException("反序列化订单支付命令失败", ex);
        }
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

    private record PreparedPayCommand(OrderCommandRecord command, boolean existed) {
    }

    private record PayRequestPayload(Long buyerUserId,
                                     String idempotencyKey,
                                     String orderNo,
                                     Long sellerUserId,
                                     Long amountCent) {
    }

    private record PayProgressPayload(String paymentNo, String reserveStatus) {
    }

    private record FailureRecord(Integer code, String message) {

        private static FailureRecord from(RuntimeException ex) {
            if (ex instanceof BizException bizException) {
                return new FailureRecord(bizException.getErrorCode().code(), bizException.getErrorCode().message());
            }
            return new FailureRecord(CommonErrorCode.SERVICE_UNAVAILABLE.code(), CommonErrorCode.SERVICE_UNAVAILABLE.message());
        }

        private static FailureRecord from(OrderCommandRecord command) {
            OrderErrorCode errorCode = OrderErrorCode.fromCode(command.resultCode());
            if (errorCode != null) {
                return new FailureRecord(errorCode.code(), errorCode.message());
            }
            String resultMessage = command.resultMessage() == null || command.resultMessage().isBlank()
                    ? CommonErrorCode.SERVICE_UNAVAILABLE.message()
                    : command.resultMessage();
            return new FailureRecord(
                    command.resultCode() == null ? CommonErrorCode.SERVICE_UNAVAILABLE.code() : command.resultCode(),
                    resultMessage
            );
        }
    }
}
