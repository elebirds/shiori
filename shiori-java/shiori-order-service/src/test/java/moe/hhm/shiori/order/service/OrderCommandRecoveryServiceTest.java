package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import moe.hhm.shiori.order.client.PaymentServiceClient;
import moe.hhm.shiori.order.client.ProductServiceClient;
import moe.hhm.shiori.order.client.ReleaseBalancePaymentSnapshot;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.domain.OrderCommandState;
import moe.hhm.shiori.order.domain.OrderCommandType;
import moe.hhm.shiori.order.model.OrderCommandRecord;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.repository.OrderCommandMapper;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCommandRecoveryServiceTest {

    private static final TransactionOperations DIRECT_TX = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    @Mock
    private OrderCommandMapper orderCommandMapper;
    @Mock
    private OrderCommandService orderCommandService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Test
    void shouldRetryLocalFinalizeBeforeCompensatingCreateCommand() {
        OrderProperties orderProperties = new OrderProperties();
        OrderMetrics orderMetrics = new OrderMetrics(new SimpleMeterRegistry());
        OrderCreateWorkflowService createWorkflowService = new OrderCreateWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        OrderCommandRecoveryService recoveryService = new OrderCommandRecoveryService(
                orderCommandMapper,
                createWorkflowService,
                new OrderPayWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                new OrderConfirmSettlementWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                orderProperties,
                orderMetrics
        );
        OrderCommandRecord command = createRemoteSucceededCommand("O202603250201");
        when(orderCommandService.orderProperties()).thenReturn(orderProperties);
        when(orderCommandService.orderMetrics()).thenReturn(orderMetrics);
        when(orderCommandService.orderMapper()).thenReturn(orderMapper);
        when(orderCommandMapper.listRecoveryCandidates(any(), anyInt())).thenReturn(List.of(command));
        when(orderMapper.findOrderNoByBuyerAndIdempotencyKey(1001L, "idem-create-1")).thenReturn(null);
        when(orderMapper.findOrderByOrderNo("O202603250201")).thenReturn(null);

        recoveryService.recoverDueCommands();

        verify(orderCommandService).persistOrder(
                eq("O202603250201"),
                eq(1001L),
                eq(2001L),
                eq(4000L),
                eq(2),
                any(LocalDateTime.class),
                eq(null),
                eq(null),
                eq(null),
                eq(true),
                eq(true),
                eq(null),
                any()
        );
        verify(productServiceClient, never()).releaseStock(anyLong(), any(), anyString(), anyLong(), any());
        verify(orderCommandMapper).markCompleted(1L, 0, "SUCCESS");
    }

    @Test
    void shouldCompensateCreateCommandUsingRecordedDeductedLines() {
        OrderProperties orderProperties = new OrderProperties();
        OrderMetrics orderMetrics = new OrderMetrics(new SimpleMeterRegistry());
        OrderCreateWorkflowService createWorkflowService = new OrderCreateWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        OrderCommandRecoveryService recoveryService = new OrderCommandRecoveryService(
                orderCommandMapper,
                createWorkflowService,
                new OrderPayWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                new OrderConfirmSettlementWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                orderProperties,
                orderMetrics
        );
        OrderCommandRecord command = createCompensatingCommand("O202603250202");
        when(orderCommandService.orderMetrics()).thenReturn(orderMetrics);
        when(orderCommandService.productServiceClient()).thenReturn(productServiceClient);
        when(orderCommandService.stockBizNo("O202603250202", 11L)).thenReturn("O202603250202:11");
        when(orderCommandMapper.listRecoveryCandidates(any(), anyInt())).thenReturn(List.of(command));

        recoveryService.recoverDueCommands();

        verify(productServiceClient).releaseStock(11L, 2, "O202603250202:11", 1001L, List.of("ROLE_USER"));
        verify(orderCommandMapper).markCompensated(eq(2L), anyString(), eq(50007), eq("重复下单请求"));
    }

    @Test
    void shouldRetryLocalFinalizeBeforeReleasingReservedPayment() {
        OrderProperties orderProperties = new OrderProperties();
        OrderMetrics orderMetrics = new OrderMetrics(new SimpleMeterRegistry());
        OrderPayWorkflowService payWorkflowService = new OrderPayWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        OrderCommandRecoveryService recoveryService = new OrderCommandRecoveryService(
                orderCommandMapper,
                new OrderCreateWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                payWorkflowService,
                new OrderConfirmSettlementWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                orderProperties,
                orderMetrics
        );
        OrderCommandRecord command = payRemoteSucceededCommand("O202603250203");
        OrderRecord order = unpaidOrder("O202603250203");
        when(orderCommandService.orderMetrics()).thenReturn(orderMetrics);
        when(orderCommandService.orderMapper()).thenReturn(orderMapper);
        when(orderCommandMapper.listRecoveryCandidates(any(), anyInt())).thenReturn(List.of(command));
        when(orderMapper.findOrderByOrderNo("O202603250203")).thenReturn(order);
        when(orderMapper.markOrderPaidByBalance(eq("O202603250203"), eq(1001L), eq("P-201"), any(), eq(1), eq(2)))
                .thenReturn(1);

        recoveryService.recoverDueCommands();

        verify(orderMapper).markOrderPaidByBalance(eq("O202603250203"), eq(1001L), eq("P-201"), any(), eq(1), eq(2));
        verify(paymentServiceClient, never()).releaseOrderPayment(anyString(), anyString(), anyLong(), any());
        verify(orderCommandMapper).markCompleted(3L, 0, "SUCCESS");
    }

    @Test
    void shouldCompleteRecoveredPayCommandWhenOrderAlreadyDeliveringWithSamePaymentNo() {
        OrderProperties orderProperties = new OrderProperties();
        OrderMetrics orderMetrics = new OrderMetrics(new SimpleMeterRegistry());
        OrderPayWorkflowService payWorkflowService = new OrderPayWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        OrderCommandRecoveryService recoveryService = new OrderCommandRecoveryService(
                orderCommandMapper,
                new OrderCreateWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                payWorkflowService,
                new OrderConfirmSettlementWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                orderProperties,
                orderMetrics
        );
        String orderNo = "O202603250205";
        OrderCommandRecord command = payRemoteSucceededCommand(orderNo);
        OrderRecord order = deliveringOrder(orderNo, "P-201");
        when(orderCommandService.orderMetrics()).thenReturn(orderMetrics);
        when(orderCommandService.orderMapper()).thenReturn(orderMapper);
        when(orderCommandMapper.listRecoveryCandidates(any(), anyInt())).thenReturn(List.of(command));
        when(orderMapper.findOrderByOrderNo(orderNo)).thenReturn(order);

        recoveryService.recoverDueCommands();

        verify(orderCommandMapper).markCompleted(3L, 0, "SUCCESS");
        verify(orderMapper, never()).markOrderPaidByBalance(anyString(), anyLong(), anyString(), any(), anyInt(), anyInt());
        verify(paymentServiceClient, never()).releaseOrderPayment(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void shouldUseFreshReserveSnapshotWhenRecoveringPreparedPayCommand() {
        OrderProperties orderProperties = new OrderProperties();
        OrderMetrics orderMetrics = new OrderMetrics(new SimpleMeterRegistry());
        OrderPayWorkflowService payWorkflowService = new OrderPayWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        OrderCommandRecoveryService recoveryService = new OrderCommandRecoveryService(
                orderCommandMapper,
                new OrderCreateWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                payWorkflowService,
                new OrderConfirmSettlementWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                orderProperties,
                orderMetrics
        );
        String orderNo = "O202603250206";
        OrderCommandRecord command = payPreparedCommand(orderNo);
        OrderRecord order = paidOrder(orderNo, "P-301");
        when(orderCommandService.orderMetrics()).thenReturn(orderMetrics);
        when(orderCommandService.orderMapper()).thenReturn(orderMapper);
        when(orderCommandService.paymentServiceClient()).thenReturn(paymentServiceClient);
        when(orderCommandMapper.listRecoveryCandidates(any(), anyInt())).thenReturn(List.of(command));
        when(orderMapper.findOrderByOrderNo(orderNo)).thenReturn(order);
        when(paymentServiceClient.reserveOrderPayment(orderNo, 1001L, 2001L, 2399L, 1001L, List.of("ROLE_USER")))
                .thenReturn(new moe.hhm.shiori.order.client.ReserveBalancePaymentSnapshot(orderNo, "P-301", "RESERVED", false));

        recoveryService.recoverDueCommands();

        verify(orderCommandMapper).markRemoteSucceeded(eq(5L), anyString());
        verify(orderCommandMapper).markCompleted(5L, 0, "SUCCESS");
        verify(paymentServiceClient, never()).releaseOrderPayment(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void shouldBackoffWhenCompensationFails() {
        OrderProperties orderProperties = new OrderProperties();
        OrderMetrics orderMetrics = new OrderMetrics(new SimpleMeterRegistry());
        OrderCreateWorkflowService createWorkflowService = new OrderCreateWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        OrderCommandRecoveryService recoveryService = new OrderCommandRecoveryService(
                orderCommandMapper,
                createWorkflowService,
                new OrderPayWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                new OrderConfirmSettlementWorkflowService(orderCommandService, orderCommandMapper, new ObjectMapper(), DIRECT_TX),
                orderProperties,
                orderMetrics
        );
        OrderCommandRecord command = createCompensatingCommand("O202603250204");
        when(orderCommandService.productServiceClient()).thenReturn(productServiceClient);
        when(orderCommandService.stockBizNo("O202603250204", 11L)).thenReturn("O202603250204:11");
        when(orderCommandMapper.listRecoveryCandidates(any(), anyInt())).thenReturn(List.of(command));
        doThrow(new IllegalStateException("release failed")).when(productServiceClient)
                .releaseStock(11L, 2, "O202603250204:11", 1001L, List.of("ROLE_USER"));

        recoveryService.recoverDueCommands();

        ArgumentCaptor<Integer> retryCountCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(orderCommandMapper).scheduleRetry(eq(4L), retryCountCaptor.capture(), eq("release failed"), any(LocalDateTime.class));
        org.assertj.core.api.Assertions.assertThat(retryCountCaptor.getValue()).isEqualTo(1);
    }

    private OrderCommandRecord createRemoteSucceededCommand(String orderNo) {
        return new OrderCommandRecord(
                1L,
                "CMD-CREATE-1",
                OrderCommandType.CREATE_ORDER.name(),
                1001L,
                "idem-create-1",
                orderNo,
                OrderCommandState.REMOTE_SUCCEEDED.name(),
                "{\"buyerUserId\":1001,\"roles\":[\"ROLE_USER\"],\"idempotencyKey\":\"idem-create-1\",\"source\":null,\"conversationId\":null}",
                "{\"preparedLines\":[{\"productId\":1,\"productNo\":\"P-1\",\"skuId\":11,\"skuNo\":\"SKU-11\",\"skuName\":\"黑色 L\",\"specJson\":\"[]\",\"priceCent\":2000,\"quantity\":2,\"subtotalCent\":4000,\"sellerUserId\":2001,\"allowMeetup\":true,\"allowDelivery\":true}],\"deductedLines\":[{\"productId\":1,\"productNo\":\"P-1\",\"skuId\":11,\"skuNo\":\"SKU-11\",\"skuName\":\"黑色 L\",\"specJson\":\"[]\",\"priceCent\":2000,\"quantity\":2,\"subtotalCent\":4000,\"sellerUserId\":2001,\"allowMeetup\":true,\"allowDelivery\":true}]}",
                null,
                null,
                0,
                null,
                null,
                LocalDateTime.now().minusSeconds(10),
                LocalDateTime.now().minusSeconds(5)
        );
    }

    private OrderCommandRecord createCompensatingCommand(String orderNo) {
        return new OrderCommandRecord(
                orderNo.endsWith("202") ? 2L : 4L,
                "CMD-CREATE-COMP",
                OrderCommandType.CREATE_ORDER.name(),
                1001L,
                "idem-create-comp",
                orderNo,
                OrderCommandState.COMPENSATING.name(),
                "{\"buyerUserId\":1001,\"roles\":[\"ROLE_USER\"],\"idempotencyKey\":\"idem-create-comp\",\"source\":null,\"conversationId\":null}",
                "{\"preparedLines\":[{\"productId\":1,\"productNo\":\"P-1\",\"skuId\":11,\"skuNo\":\"SKU-11\",\"skuName\":\"黑色 L\",\"specJson\":\"[]\",\"priceCent\":2000,\"quantity\":2,\"subtotalCent\":4000,\"sellerUserId\":2001,\"allowMeetup\":true,\"allowDelivery\":true}],\"deductedLines\":[{\"productId\":1,\"productNo\":\"P-1\",\"skuId\":11,\"skuNo\":\"SKU-11\",\"skuName\":\"黑色 L\",\"specJson\":\"[]\",\"priceCent\":2000,\"quantity\":2,\"subtotalCent\":4000,\"sellerUserId\":2001,\"allowMeetup\":true,\"allowDelivery\":true}]}",
                50007,
                "重复下单请求",
                0,
                null,
                null,
                LocalDateTime.now().minusSeconds(10),
                LocalDateTime.now().minusSeconds(5)
        );
    }

    private OrderCommandRecord payRemoteSucceededCommand(String orderNo) {
        return new OrderCommandRecord(
                3L,
                "CMD-PAY-1",
                OrderCommandType.PAY_BALANCE_ORDER.name(),
                1001L,
                "idem-pay-1",
                orderNo,
                OrderCommandState.REMOTE_SUCCEEDED.name(),
                "{\"buyerUserId\":1001,\"idempotencyKey\":\"idem-pay-1\",\"orderNo\":\"" + orderNo + "\",\"sellerUserId\":2001,\"amountCent\":2399}",
                "{\"paymentNo\":\"P-201\",\"reserveStatus\":\"RESERVED\"}",
                null,
                null,
                0,
                null,
                null,
                LocalDateTime.now().minusSeconds(10),
                LocalDateTime.now().minusSeconds(5)
        );
    }

    private OrderCommandRecord payPreparedCommand(String orderNo) {
        return new OrderCommandRecord(
                5L,
                "CMD-PAY-PREPARED",
                OrderCommandType.PAY_BALANCE_ORDER.name(),
                1001L,
                "idem-pay-prepared",
                orderNo,
                OrderCommandState.PREPARED.name(),
                "{\"buyerUserId\":1001,\"idempotencyKey\":\"idem-pay-prepared\",\"orderNo\":\"" + orderNo + "\",\"sellerUserId\":2001,\"amountCent\":2399}",
                "{\"paymentNo\":null,\"reserveStatus\":null}",
                null,
                null,
                0,
                null,
                null,
                LocalDateTime.now().minusSeconds(10),
                LocalDateTime.now().minusSeconds(5)
        );
    }

    private OrderRecord unpaidOrder(String orderNo) {
        return new OrderRecord(
                1L,
                orderNo,
                1001L,
                2001L,
                1,
                2399L,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now().plusMinutes(15),
                null,
                null,
                null,
                null,
                null,
                1,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().minusMinutes(1)
        );
    }

    private OrderRecord paidOrder(String orderNo, String paymentNo) {
        return new OrderRecord(
                1L,
                orderNo,
                1001L,
                2001L,
                2,
                2399L,
                1,
                paymentNo,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now().plusMinutes(15),
                LocalDateTime.now().minusMinutes(2),
                null,
                null,
                null,
                null,
                1,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                LocalDateTime.now().minusMinutes(3),
                LocalDateTime.now().minusMinutes(1)
        );
    }

    private OrderRecord deliveringOrder(String orderNo, String paymentNo) {
        return new OrderRecord(
                1L,
                orderNo,
                1001L,
                2001L,
                4,
                2399L,
                1,
                paymentNo,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now().plusMinutes(15),
                LocalDateTime.now().minusMinutes(3),
                LocalDateTime.now().minusMinutes(2),
                null,
                null,
                null,
                1,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                LocalDateTime.now().minusMinutes(4),
                LocalDateTime.now().minusMinutes(1)
        );
    }
}
