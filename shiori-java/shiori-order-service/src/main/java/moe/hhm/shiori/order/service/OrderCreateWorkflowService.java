package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.dto.CreateOrderRequest;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
import moe.hhm.shiori.order.model.OrderCommandEntity;
import moe.hhm.shiori.order.model.OrderCommandRecord;
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
public class OrderCreateWorkflowService {

    private static final String SUCCESS_MESSAGE = "SUCCESS";

    private final OrderCommandService orderCommandService;
    private final OrderCommandMapper orderCommandMapper;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;

    @Autowired
    public OrderCreateWorkflowService(OrderCommandService orderCommandService,
                                      OrderCommandMapper orderCommandMapper,
                                      ObjectMapper objectMapper,
                                      PlatformTransactionManager transactionManager) {
        this(orderCommandService, orderCommandMapper, objectMapper, new TransactionTemplate(transactionManager));
    }

    OrderCreateWorkflowService(OrderCommandService orderCommandService,
                               OrderCommandMapper orderCommandMapper,
                               ObjectMapper objectMapper,
                               TransactionOperations transactionOperations) {
        this.orderCommandService = orderCommandService;
        this.orderCommandMapper = orderCommandMapper;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations;
    }

    public CreateOrderResponse createOrder(Long buyerUserId,
                                           List<String> roles,
                                           String idempotencyKey,
                                           CreateOrderRequest request) {
        String normalizedIdempotencyKey = orderCommandService.requireIdempotencyKey(idempotencyKey);
        String existedOrderNo =
                orderCommandService.orderMapper().findOrderNoByBuyerAndIdempotencyKey(buyerUserId, normalizedIdempotencyKey);
        if (hasText(existedOrderNo)) {
            orderCommandService.orderMetrics().incIdempotency(OrderCommandService.OP_CREATE, "hit");
            return orderCommandService.buildIdempotentCreateResponse(existedOrderNo);
        }
        orderCommandService.orderMetrics().incIdempotency(OrderCommandService.OP_CREATE, "miss");

        OrderCommandRecord existedCommand = orderCommandMapper.findByOperatorAndTypeAndIdempotencyKey(
                buyerUserId,
                "CREATE_ORDER",
                normalizedIdempotencyKey
        );
        if (existedCommand != null) {
            return resolveExistingCommand(existedCommand);
        }

        String normalizedSource = orderCommandService.normalizeSource(request == null ? null : request.source());
        Long chatConversationId = orderCommandService.normalizeConversationId(
                request == null ? null : request.conversationId(),
                normalizedSource
        );
        List<OrderCommandService.PreparedOrderLine> lines = orderCommandService.prepareOrderLines(buyerUserId, roles, request);
        orderCommandService.validateChatSourceContext(buyerUserId, roles, normalizedSource, chatConversationId, lines);

        PreparedCreateContext context = PreparedCreateContext.from(
                buyerUserId,
                roles,
                normalizedIdempotencyKey,
                normalizedSource,
                chatConversationId,
                orderCommandService.generateOrderNo(),
                LocalDateTime.now().plusMinutes(orderCommandService.orderProperties().getTimeoutMinutes()),
                lines
        );

        PreparedCommand preparedCommand = prepareCreateCommand(context);
        if (preparedCommand.existed()) {
            return resolveExistingCommand(preparedCommand.command());
        }

        executeRemoteDeduction(context, preparedCommand.command());
        return finalizeCreate(context, preparedCommand.command(), true);
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

    private PreparedCommand prepareCreateCommand(PreparedCreateContext context) {
        return transactionOperations.execute(status -> {
            OrderCommandRecord existed = orderCommandMapper.findByOperatorAndTypeAndIdempotencyKey(
                    context.buyerUserId(),
                    "CREATE_ORDER",
                    context.idempotencyKey()
            );
            if (existed != null) {
                return new PreparedCommand(existed, true);
            }

            OrderCommandEntity entity = new OrderCommandEntity();
            entity.setCommandNo("cmd-" + UUID.randomUUID());
            entity.setCommandType("CREATE_ORDER");
            entity.setOperatorUserId(context.buyerUserId());
            entity.setIdempotencyKey(context.idempotencyKey());
            entity.setOrderNo(context.orderNo());
            entity.setStatus("PREPARED");
            entity.setRequestPayload(writeValue(new CreateRequestPayload(
                    context.buyerUserId(),
                    context.roles(),
                    context.idempotencyKey(),
                    context.source(),
                    context.chatConversationId()
            )));
            entity.setProgressPayload(writeValue(CreateProgressPayload.from(context.lines(), List.of())));
            entity.setRetryCount(0);

            try {
                orderCommandMapper.insertOrderCommand(entity);
                orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "PREPARED");
                return new PreparedCommand(requireCommand(entity.getCommandNo()), false);
            } catch (DuplicateKeyException ex) {
                OrderCommandRecord duplicated = orderCommandMapper.findByOperatorAndTypeAndIdempotencyKey(
                        context.buyerUserId(),
                        "CREATE_ORDER",
                        context.idempotencyKey()
                );
                if (duplicated == null) {
                    throw ex;
                }
                return new PreparedCommand(duplicated, true);
            }
        });
    }

    private void executeRemoteDeduction(PreparedCreateContext context, OrderCommandRecord command) {
        List<CreateLinePayload> deductedLines = new ArrayList<>();
        for (OrderCommandService.PreparedOrderLine line : context.lines()) {
            try {
                orderCommandService.productServiceClient().deductStock(
                        line.skuId(),
                        line.quantity(),
                        orderCommandService.stockBizNo(context.orderNo(), line.skuId()),
                        context.buyerUserId(),
                        context.roles()
                );
            } catch (RuntimeException ex) {
                persistCreateFailure(command, context, deductedLines, ex);
                throw ex;
            }

            deductedLines.add(CreateLinePayload.from(line));
            CreateProgressPayload progress = CreateProgressPayload.from(context.lines(), deductedLines);
            transactionOperations.executeWithoutResult(status ->
                    orderCommandMapper.markPreparedProgress(command.id(), writeValue(progress)));
        }

        CreateProgressPayload progress = CreateProgressPayload.from(context.lines(), deductedLines);
        transactionOperations.executeWithoutResult(status ->
                orderCommandMapper.markRemoteSucceeded(command.id(), writeValue(progress)));
        orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "REMOTE_SUCCEEDED");
    }

    private void persistCreateFailure(OrderCommandRecord command,
                                      PreparedCreateContext context,
                                      List<CreateLinePayload> deductedLines,
                                      RuntimeException ex) {
        FailureRecord failure = FailureRecord.from(ex);
        if (deductedLines.isEmpty()) {
            transactionOperations.executeWithoutResult(status ->
                    orderCommandMapper.markFailed(command.id(), failure.code(), failure.message(), trimError(ex)));
            orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "FAILED");
            return;
        }

        CreateProgressPayload progress = CreateProgressPayload.from(context.lines(), deductedLines);
        boolean released = true;
        for (CreateLinePayload deductedLine : deductedLines) {
            try {
                orderCommandService.productServiceClient().releaseStock(
                        deductedLine.skuId(),
                        deductedLine.quantity(),
                        orderCommandService.stockBizNo(context.orderNo(), deductedLine.skuId()),
                        context.buyerUserId(),
                        context.roles()
                );
            } catch (RuntimeException releaseEx) {
                released = false;
            }
        }

        if (released) {
            transactionOperations.executeWithoutResult(status ->
                    orderCommandMapper.markCompensated(
                            command.id(),
                            writeValue(progress),
                            failure.code(),
                            failure.message()
                    ));
            orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "COMPENSATED");
            orderCommandService.orderMetrics().incOrderCommandCompensation("CREATE_ORDER", "success");
            return;
        }

        int nextRetryCount = nextRetryCount(command.retryCount());
        transactionOperations.executeWithoutResult(status ->
                orderCommandMapper.markCompensating(
                        command.id(),
                        writeValue(progress),
                        failure.code(),
                        failure.message(),
                        nextRetryCount,
                        trimError(ex),
                        LocalDateTime.now().plusSeconds(calcBackoffSeconds(nextRetryCount))
                ));
        orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "COMPENSATING");
        orderCommandService.orderMetrics().incOrderCommandCompensation("CREATE_ORDER", "retry");
    }

    private void recoverPreparedCommand(OrderCommandRecord command) {
        CreateRequestPayload requestPayload = readValue(command.requestPayload(), CreateRequestPayload.class);
        CreateProgressPayload progressPayload = readValue(command.progressPayload(), CreateProgressPayload.class);
        PreparedCreateContext context = PreparedCreateContext.fromRecovered(
                command,
                requestPayload,
                progressPayload,
                orderCommandService.orderProperties().getTimeoutMinutes()
        );
        List<CreateLinePayload> deductedLines = new ArrayList<>(safeDeductedLines(progressPayload));
        for (CreateLinePayload preparedLine : safePreparedLines(progressPayload)) {
            if (containsDeductedLine(deductedLines, preparedLine)) {
                continue;
            }
            orderCommandService.productServiceClient().deductStock(
                    preparedLine.skuId(),
                    preparedLine.quantity(),
                    orderCommandService.stockBizNo(context.orderNo(), preparedLine.skuId()),
                    context.buyerUserId(),
                    context.roles()
            );
            deductedLines.add(preparedLine);
            transactionOperations.executeWithoutResult(status ->
                    orderCommandMapper.markPreparedProgress(
                            command.id(),
                            writeValue(new CreateProgressPayload(safePreparedLines(progressPayload), List.copyOf(deductedLines)))
                    ));
        }
        transactionOperations.executeWithoutResult(status ->
                orderCommandMapper.markRemoteSucceeded(
                        command.id(),
                        writeValue(new CreateProgressPayload(safePreparedLines(progressPayload), List.copyOf(deductedLines)))
                ));
        orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "REMOTE_SUCCEEDED");
        recoverRemoteSucceededCommand(command);
    }

    private void recoverRemoteSucceededCommand(OrderCommandRecord command) {
        CreateRequestPayload requestPayload = readValue(command.requestPayload(), CreateRequestPayload.class);
        CreateProgressPayload progressPayload = readValue(command.progressPayload(), CreateProgressPayload.class);
        PreparedCreateContext context = PreparedCreateContext.fromRecovered(
                command,
                requestPayload,
                progressPayload,
                orderCommandService.orderProperties().getTimeoutMinutes()
        );
        String idempotentOrderNo = orderCommandService.orderMapper().findOrderNoByBuyerAndIdempotencyKey(
                context.buyerUserId(),
                context.idempotencyKey()
        );
        if (hasText(idempotentOrderNo)) {
            transactionOperations.executeWithoutResult(status ->
                    orderCommandMapper.markCompleted(command.id(), 0, SUCCESS_MESSAGE));
            orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "COMPLETED");
            return;
        }
        if (orderCommandService.orderMapper().findOrderByOrderNo(context.orderNo()) != null) {
            transactionOperations.executeWithoutResult(status ->
                    orderCommandMapper.markCompleted(command.id(), 0, SUCCESS_MESSAGE));
            orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "COMPLETED");
            return;
        }
        try {
            finalizeCreate(context, command, false);
        } catch (BizException ex) {
            compensateRecoveredCommand(command, FailureRecord.from(ex));
        }
    }

    private void compensateRecoveredCommand(OrderCommandRecord command, FailureRecord fallbackFailure) {
        CreateRequestPayload requestPayload = readValue(command.requestPayload(), CreateRequestPayload.class);
        CreateProgressPayload progressPayload = readValue(command.progressPayload(), CreateProgressPayload.class);
        for (CreateLinePayload deductedLine : safeDeductedLines(progressPayload)) {
            orderCommandService.productServiceClient().releaseStock(
                    deductedLine.skuId(),
                    deductedLine.quantity(),
                    orderCommandService.stockBizNo(command.orderNo(), deductedLine.skuId()),
                    requestPayload.buyerUserId(),
                    safeRoles(requestPayload.roles())
            );
        }
        FailureRecord failure = command.resultCode() != null || command.resultMessage() != null
                ? FailureRecord.from(command)
                : fallbackFailure;
        transactionOperations.executeWithoutResult(status ->
                orderCommandMapper.markCompensated(
                        command.id(),
                        writeValue(progressPayload),
                        failure.code(),
                        failure.message()
                ));
        orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "COMPENSATED");
        orderCommandService.orderMetrics().incOrderCommandCompensation("CREATE_ORDER", "success");
    }

    private CreateOrderResponse finalizeCreate(PreparedCreateContext context,
                                               OrderCommandRecord command,
                                               boolean scheduleRetryOnFailure) {
        try {
            return transactionOperations.execute(status -> {
                try {
                    orderCommandService.orderMapper().insertCreateIdempotency(
                            context.buyerUserId(),
                            context.idempotencyKey(),
                            context.orderNo()
                    );
                } catch (DuplicateKeyException ex) {
                    orderCommandService.orderMetrics().incIdempotency(OrderCommandService.OP_CREATE, "hit");
                    orderCommandMapper.markCompleted(command.id(), 0, SUCCESS_MESSAGE);
                    orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "COMPLETED");
                    return orderCommandService.buildIdempotentCreateResponseFromKey(
                            context.buyerUserId(),
                            context.idempotencyKey()
                    );
                }

                Long listingId = OrderCommandService.SOURCE_CHAT.equals(context.source())
                        ? context.lines().getFirst().productId()
                        : null;
                orderCommandService.persistOrder(
                        context.orderNo(),
                        context.buyerUserId(),
                        context.sellerUserId(),
                        context.totalAmountCent(),
                        context.itemCount(),
                        context.timeoutAt(),
                        context.source(),
                        context.chatConversationId(),
                        listingId,
                        context.allowMeetup(),
                        context.allowDelivery(),
                        context.initialFulfillmentMode(),
                        context.lines()
                );
                orderCommandService.appendOrderCreatedOutbox(
                        context.orderNo(),
                        context.buyerUserId(),
                        context.sellerUserId(),
                        context.totalAmountCent(),
                        context.itemCount()
                );
                orderCommandService.appendOrderTimeoutOutbox(context.orderNo(), context.buyerUserId());
                orderCommandService.orderMetrics().incStateTransition("NEW", "UNPAID", OrderCommandService.SOURCE_BUYER);
                if (OrderCommandService.SOURCE_CHAT.equals(context.source()) && context.chatConversationId() != null) {
                    orderCommandService.orderMetrics().incChatToOrderSubmit(context.source());
                    orderCommandService.orderMetrics().incChatTradeStatusCardSent("ORDER_CREATED");
                }
                orderCommandMapper.markCompleted(command.id(), 0, SUCCESS_MESSAGE);
                orderCommandService.orderMetrics().incOrderCommand("CREATE_ORDER", "COMPLETED");
                return new CreateOrderResponse(
                        context.orderNo(),
                        "UNPAID",
                        context.totalAmountCent(),
                        context.itemCount(),
                        false
                );
            });
        } catch (RuntimeException ex) {
            if (scheduleRetryOnFailure) {
                int nextRetryCount = nextRetryCount(command.retryCount());
                transactionOperations.executeWithoutResult(status ->
                        orderCommandMapper.scheduleRetry(
                                command.id(),
                                nextRetryCount,
                                trimError(ex),
                                LocalDateTime.now().plusSeconds(calcBackoffSeconds(nextRetryCount))
                        ));
            }
            throw ex;
        }
    }

    private CreateOrderResponse resolveExistingCommand(OrderCommandRecord command) {
        return switch (command.status()) {
            case "COMPLETED" -> {
                orderCommandService.orderMetrics().incIdempotency(OrderCommandService.OP_CREATE, "hit");
                yield orderCommandService.buildIdempotentCreateResponse(command.orderNo());
            }
            case "PREPARED", "REMOTE_SUCCEEDED", "COMPENSATING" -> {
                orderCommandService.orderMetrics().incOrderCommandProcessing("CREATE_ORDER");
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
            throw new IllegalStateException("创建命令后未能读回命令日志");
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
            throw new IllegalStateException("序列化订单命令失败", ex);
        }
    }

    private <T> T readValue(String value, Class<T> targetType) {
        try {
            return objectMapper.readValue(value, targetType);
        } catch (JacksonException ex) {
            throw new IllegalStateException("反序列化订单命令失败", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimError(Throwable ex) {
        if (ex == null || !hasText(ex.getMessage())) {
            return "unknown";
        }
        String normalized = ex.getMessage().trim();
        if (normalized.length() <= 500) {
            return normalized;
        }
        return normalized.substring(0, 500);
    }

    private List<CreateLinePayload> safePreparedLines(CreateProgressPayload payload) {
        if (payload == null || payload.preparedLines() == null) {
            return List.of();
        }
        return payload.preparedLines();
    }

    private List<CreateLinePayload> safeDeductedLines(CreateProgressPayload payload) {
        if (payload == null || payload.deductedLines() == null) {
            return List.of();
        }
        return payload.deductedLines();
    }

    private boolean containsDeductedLine(List<CreateLinePayload> deductedLines, CreateLinePayload candidate) {
        return deductedLines.stream()
                .anyMatch(line -> line.skuId().equals(candidate.skuId()) && line.quantity().equals(candidate.quantity()));
    }

    private List<String> safeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles;
    }

    private record PreparedCommand(OrderCommandRecord command, boolean existed) {
    }

    private record PreparedCreateContext(Long buyerUserId,
                                         List<String> roles,
                                         String idempotencyKey,
                                         String source,
                                         Long chatConversationId,
                                         String orderNo,
                                         LocalDateTime timeoutAt,
                                         List<OrderCommandService.PreparedOrderLine> lines,
                                         Long sellerUserId,
                                         long totalAmountCent,
                                         int itemCount,
                                         boolean allowMeetup,
                                         boolean allowDelivery,
                                         String initialFulfillmentMode) {

        private static PreparedCreateContext from(Long buyerUserId,
                                                  List<String> roles,
                                                  String idempotencyKey,
                                                  String source,
                                                  Long chatConversationId,
                                                  String orderNo,
                                                  LocalDateTime timeoutAt,
                                                  List<OrderCommandService.PreparedOrderLine> lines) {
            long totalAmountCent = lines.stream().mapToLong(OrderCommandService.PreparedOrderLine::subtotalCent).sum();
            int itemCount = lines.stream().mapToInt(OrderCommandService.PreparedOrderLine::quantity).sum();
            boolean allowMeetup = lines.stream().allMatch(OrderCommandService.PreparedOrderLine::allowMeetup);
            boolean allowDelivery = lines.stream().allMatch(OrderCommandService.PreparedOrderLine::allowDelivery);
            if (!allowMeetup && !allowDelivery) {
                throw new BizException(OrderErrorCode.ORDER_FULFILLMENT_INVALID, HttpStatus.BAD_REQUEST);
            }
            String fulfillmentMode = null;
            if (allowMeetup && !allowDelivery) {
                fulfillmentMode = "MEETUP";
            } else if (!allowMeetup) {
                fulfillmentMode = "DELIVERY";
            }
            return new PreparedCreateContext(
                    buyerUserId,
                    roles == null ? List.of() : List.copyOf(roles),
                    idempotencyKey,
                    source,
                    chatConversationId,
                    orderNo,
                    timeoutAt,
                    List.copyOf(lines),
                    lines.getFirst().sellerUserId(),
                    totalAmountCent,
                    itemCount,
                    allowMeetup,
                    allowDelivery,
                    fulfillmentMode
            );
        }

        private static PreparedCreateContext fromRecovered(OrderCommandRecord command,
                                                           CreateRequestPayload requestPayload,
                                                           CreateProgressPayload progressPayload,
                                                           long timeoutMinutes) {
            List<CreateLinePayload> preparedLines = progressPayload == null || progressPayload.preparedLines() == null
                    ? List.of()
                    : progressPayload.preparedLines();
            List<OrderCommandService.PreparedOrderLine> lines = preparedLines.stream()
                    .map(CreateLinePayload::toPreparedOrderLine)
                    .toList();
            return from(
                    requestPayload.buyerUserId(),
                    requestPayload.roles(),
                    requestPayload.idempotencyKey(),
                    requestPayload.source(),
                    requestPayload.conversationId(),
                    command.orderNo(),
                    command.createdAt().plusMinutes(timeoutMinutes),
                    lines
            );
        }
    }

    private record CreateRequestPayload(Long buyerUserId,
                                        List<String> roles,
                                        String idempotencyKey,
                                        String source,
                                        Long conversationId) {
    }

    private record CreateProgressPayload(List<CreateLinePayload> preparedLines,
                                         List<CreateLinePayload> deductedLines) {

        private static CreateProgressPayload from(List<OrderCommandService.PreparedOrderLine> preparedLines,
                                                  List<CreateLinePayload> deductedLines) {
            return new CreateProgressPayload(
                    preparedLines.stream().map(CreateLinePayload::from).toList(),
                    List.copyOf(deductedLines)
            );
        }
    }

    private record CreateLinePayload(Long productId,
                                     String productNo,
                                     Long skuId,
                                     String skuNo,
                                     String skuName,
                                     String specJson,
                                     Long priceCent,
                                     Integer quantity,
                                     Long subtotalCent,
                                     Long sellerUserId,
                                     boolean allowMeetup,
                                     boolean allowDelivery) {

        private static CreateLinePayload from(OrderCommandService.PreparedOrderLine line) {
            return new CreateLinePayload(
                    line.productId(),
                    line.productNo(),
                    line.skuId(),
                    line.skuNo(),
                    line.skuName(),
                    line.specJson(),
                    line.priceCent(),
                    line.quantity(),
                    line.subtotalCent(),
                    line.sellerUserId(),
                    line.allowMeetup(),
                    line.allowDelivery()
            );
        }

        private OrderCommandService.PreparedOrderLine toPreparedOrderLine() {
            return new OrderCommandService.PreparedOrderLine(
                    productId,
                    productNo,
                    skuId,
                    skuNo,
                    skuName,
                    specJson,
                    priceCent,
                    quantity,
                    subtotalCent,
                    sellerUserId,
                    allowMeetup,
                    allowDelivery
            );
        }
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
