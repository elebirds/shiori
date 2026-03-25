package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import moe.hhm.shiori.order.client.PaymentServiceClient;
import moe.hhm.shiori.order.client.SettleBalancePaymentSnapshot;
import moe.hhm.shiori.order.model.OrderCommandRecord;
import moe.hhm.shiori.order.repository.OrderCommandMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderConfirmSettlementWorkflowServiceTest {

    private static final TransactionOperations DIRECT_TX = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    @Mock
    private OrderCommandService orderCommandService;
    @Mock
    private OrderCommandMapper orderCommandMapper;
    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Test
    void shouldRecoverPreparedConfirmSettlementCommandAsync() {
        OrderConfirmSettlementWorkflowService workflowService = new OrderConfirmSettlementWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        when(orderCommandService.orderMetrics()).thenReturn(new OrderMetrics(new SimpleMeterRegistry()));
        when(orderCommandService.paymentServiceClient()).thenReturn(paymentServiceClient);
        when(paymentServiceClient.settleOrderPayment("O202603260001", "BUYER", 1001L, 1001L, List.of("ROLE_USER")))
                .thenReturn(new SettleBalancePaymentSnapshot("O202603260001", "P-001", "SETTLED", false));

        workflowService.recover(preparedCommand("O202603260001"));

        InOrder inOrder = inOrder(orderCommandMapper);
        inOrder.verify(orderCommandMapper).markRemoteSucceeded(eq(21L), anyString());
        inOrder.verify(orderCommandMapper).markCompleted(21L, 0, "SUCCESS");
    }

    private OrderCommandRecord preparedCommand(String orderNo) {
        return new OrderCommandRecord(
                21L,
                "CMD-CONFIRM-21",
                "CONFIRM_RECEIPT_SETTLEMENT",
                1001L,
                orderNo,
                orderNo,
                "PREPARED",
                "{\"buyerUserId\":1001,\"orderNo\":\"" + orderNo + "\"}",
                "{\"paymentNo\":null,\"settleStatus\":null}",
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
