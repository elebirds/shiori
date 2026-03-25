package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.client.PaymentServiceClient;
import moe.hhm.shiori.order.client.ReleaseBalancePaymentSnapshot;
import moe.hhm.shiori.order.client.ReserveBalancePaymentSnapshot;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.model.OrderCommandEntity;
import moe.hhm.shiori.order.model.OrderCommandRecord;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.repository.OrderCommandMapper;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPayWorkflowServiceTest {

    private static final TransactionOperations DIRECT_TX = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    @Mock
    private OrderCommandService orderCommandService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private OrderCommandMapper orderCommandMapper;

    @Test
    void shouldReturnProcessingWhenBalancePayCommandAlreadyInFlight() {
        OrderPayWorkflowService workflowService = new OrderPayWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        OrderRecord order = unpaidOrder("O202603250101");
        when(orderCommandService.orderMetrics()).thenReturn(new OrderMetrics(new SimpleMeterRegistry()));
        when(orderCommandService.requireIdempotencyKey("idem-pay-1")).thenReturn("idem-pay-1");
        when(orderCommandService.requireOrder("O202603250101")).thenReturn(order);
        when(orderCommandService.hasOperateIdempotencyHit(1001L, "PAY", "idem-pay-1", "O202603250101")).thenReturn(false);
        when(orderCommandMapper.findByOperatorAndTypeAndIdempotencyKey(1001L, "PAY_BALANCE_ORDER", "idem-pay-1"))
                .thenReturn(commandRecord("PREPARED", "O202603250101", 1L));

        assertThatThrownBy(() -> workflowService.payOrderByBalance(1001L, "O202603250101", "idem-pay-1"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50039);
    }

    @Test
    void shouldPersistPaymentNoBeforeFinalOrderPaidTransaction() {
        OrderPayWorkflowService workflowService = new OrderPayWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        OrderRecord order = unpaidOrder("O202603250102");
        when(orderCommandService.orderMetrics()).thenReturn(new OrderMetrics(new SimpleMeterRegistry()));
        when(orderCommandService.requireIdempotencyKey("idem-pay-2")).thenReturn("idem-pay-2");
        when(orderCommandService.requireOrder("O202603250102")).thenReturn(order);
        when(orderCommandService.orderMapper()).thenReturn(orderMapper);
        when(orderCommandService.paymentServiceClient()).thenReturn(paymentServiceClient);
        when(orderCommandService.hasOperateIdempotencyHit(1001L, "PAY", "idem-pay-2", "O202603250102")).thenReturn(false);
        when(orderMapper.findOrderByOrderNoForUpdate("O202603250102")).thenReturn(order);
        doAnswer(invocation -> {
            OrderCommandEntity entity = invocation.getArgument(0);
            entity.setId(2L);
            return 1;
        }).when(orderCommandMapper).insertOrderCommand(any(OrderCommandEntity.class));
        when(orderCommandMapper.findByCommandNo(anyString())).thenReturn(commandRecord("PREPARED", "O202603250102", 2L));
        when(paymentServiceClient.reserveOrderPayment("O202603250102", 1001L, 2001L, 2399L, 1001L, java.util.List.of("ROLE_USER")))
                .thenReturn(new ReserveBalancePaymentSnapshot("O202603250102", "P-001", "RESERVED", false));
        when(orderMapper.markOrderPaidByBalance(eq("O202603250102"), eq(1001L), eq("P-001"), any(), eq(1), eq(2)))
                .thenReturn(1);

        workflowService.payOrderByBalance(1001L, "O202603250102", "idem-pay-2");

        InOrder inOrder = inOrder(orderCommandMapper, orderMapper);
        inOrder.verify(orderCommandMapper).markRemoteSucceeded(eq(2L), anyString());
        inOrder.verify(orderMapper).markOrderPaidByBalance(eq("O202603250102"), eq(1001L), eq("P-001"), any(), eq(1), eq(2));
    }

    @Test
    void shouldLeaveRecoverableCommandWhenReserveSucceededButMarkPaidFails() {
        OrderPayWorkflowService workflowService = new OrderPayWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        OrderRecord order = unpaidOrder("O202603250103");
        when(orderCommandService.orderMetrics()).thenReturn(new OrderMetrics(new SimpleMeterRegistry()));
        when(orderCommandService.orderProperties()).thenReturn(new OrderProperties());
        when(orderCommandService.requireIdempotencyKey("idem-pay-3")).thenReturn("idem-pay-3");
        when(orderCommandService.requireOrder("O202603250103")).thenReturn(order);
        when(orderCommandService.orderMapper()).thenReturn(orderMapper);
        when(orderCommandService.paymentServiceClient()).thenReturn(paymentServiceClient);
        when(orderCommandService.hasOperateIdempotencyHit(1001L, "PAY", "idem-pay-3", "O202603250103")).thenReturn(false);
        when(orderMapper.findOrderByOrderNoForUpdate("O202603250103")).thenReturn(order);
        doAnswer(invocation -> {
            OrderCommandEntity entity = invocation.getArgument(0);
            entity.setId(3L);
            return 1;
        }).when(orderCommandMapper).insertOrderCommand(any(OrderCommandEntity.class));
        when(orderCommandMapper.findByCommandNo(anyString())).thenReturn(commandRecord("PREPARED", "O202603250103", 3L));
        when(paymentServiceClient.reserveOrderPayment("O202603250103", 1001L, 2001L, 2399L, 1001L, java.util.List.of("ROLE_USER")))
                .thenReturn(new ReserveBalancePaymentSnapshot("O202603250103", "P-003", "RESERVED", false));
        when(orderMapper.markOrderPaidByBalance(eq("O202603250103"), eq(1001L), eq("P-003"), any(), eq(1), eq(2)))
                .thenReturn(1);
        doThrow(new IllegalStateException("append outbox failed")).when(orderCommandService)
                .appendOrderPaidOutbox("O202603250103", "P-003", 1001L, 2001L, 2399L);

        assertThatThrownBy(() -> workflowService.payOrderByBalance(1001L, "O202603250103", "idem-pay-3"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("append outbox failed");

        verify(orderCommandMapper).markRemoteSucceeded(eq(3L), anyString());
        verify(paymentServiceClient, never()).releaseOrderPayment(anyString(), anyString(), anyLong(), any());
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

    private OrderCommandRecord commandRecord(String status, String orderNo, Long id) {
        return new OrderCommandRecord(
                id,
                "CMD-PAY-" + id,
                "PAY_BALANCE_ORDER",
                1001L,
                "idem-pay-" + id,
                orderNo,
                status,
                "{\"buyerUserId\":1001}",
                "{\"paymentNo\":null}",
                null,
                null,
                0,
                null,
                null,
                LocalDateTime.now().minusSeconds(10),
                LocalDateTime.now().minusSeconds(5)
        );
    }
}
