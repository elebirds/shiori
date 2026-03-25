package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.client.ProductServiceClient;
import moe.hhm.shiori.order.client.StockOperateSnapshot;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.dto.CreateOrderItem;
import moe.hhm.shiori.order.dto.CreateOrderRequest;
import moe.hhm.shiori.order.model.OrderCommandEntity;
import moe.hhm.shiori.order.model.OrderCommandRecord;
import moe.hhm.shiori.order.repository.OrderCommandMapper;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
class OrderCreateWorkflowServiceTest {

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
    private ProductServiceClient productServiceClient;
    @Mock
    private OrderCommandMapper orderCommandMapper;

    @Test
    void shouldReturnProcessingWhenCreateCommandAlreadyInFlight() {
        OrderCreateWorkflowService workflowService = new OrderCreateWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItem(1L, 11L, 1)), null, null);
        when(orderCommandService.orderMetrics()).thenReturn(new OrderMetrics(new SimpleMeterRegistry()));
        when(orderCommandService.requireIdempotencyKey("idem-1")).thenReturn("idem-1");
        when(orderCommandService.orderMapper()).thenReturn(orderMapper);
        when(orderMapper.findOrderNoByBuyerAndIdempotencyKey(1001L, "idem-1")).thenReturn(null);
        when(orderCommandMapper.findByOperatorAndTypeAndIdempotencyKey(1001L, "CREATE_ORDER", "idem-1"))
                .thenReturn(commandRecord("PREPARED"));

        assertThatThrownBy(() -> workflowService.createOrder(1001L, List.of("ROLE_USER"), "idem-1", request))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50039);
    }

    @Test
    void shouldPersistDeductedLinesProgressBeforeFinalLocalPersist() {
        OrderCreateWorkflowService workflowService = new OrderCreateWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItem(1L, 11L, 2)), null, null);
        OrderCommandService.PreparedOrderLine line = new OrderCommandService.PreparedOrderLine(
                1L,
                "P-1",
                11L,
                "SKU-11",
                "黑色 L",
                "[]",
                2000L,
                2,
                4000L,
                2001L,
                true,
                true
        );
        when(orderCommandService.orderMetrics()).thenReturn(new OrderMetrics(new SimpleMeterRegistry()));
        when(orderCommandService.orderProperties()).thenReturn(new OrderProperties());
        when(orderCommandService.requireIdempotencyKey("idem-2")).thenReturn("idem-2");
        when(orderCommandService.normalizeSource(null)).thenReturn(null);
        when(orderCommandService.normalizeConversationId(null, null)).thenReturn(null);
        when(orderCommandService.orderMapper()).thenReturn(orderMapper);
        when(orderCommandService.prepareOrderLines(1001L, List.of("ROLE_USER"), request)).thenReturn(List.of(line));
        when(orderMapper.findOrderNoByBuyerAndIdempotencyKey(1001L, "idem-2")).thenReturn(null);
        when(orderCommandService.generateOrderNo()).thenReturn("O202603250002");
        doAnswer(invocation -> {
            OrderCommandEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(orderCommandMapper).insertOrderCommand(any(OrderCommandEntity.class));
        when(orderCommandMapper.findByCommandNo(anyString())).thenReturn(commandRecord("PREPARED", "O202603250002", 1L));
        when(productServiceClient.deductStock(11L, 2, "O202603250002:11", 1001L, List.of("ROLE_USER")))
                .thenReturn(new StockOperateSnapshot(true, false, "O202603250002:11", 11L, 2, 98));
        when(orderCommandService.productServiceClient()).thenReturn(productServiceClient);
        when(orderCommandService.stockBizNo("O202603250002", 11L)).thenReturn("O202603250002:11");

        workflowService.createOrder(1001L, List.of("ROLE_USER"), "idem-2", request);

        InOrder inOrder = inOrder(orderCommandMapper, orderCommandService);
        inOrder.verify(orderCommandMapper).markPreparedProgress(eq(1L), anyString());
        inOrder.verify(orderCommandMapper).markRemoteSucceeded(eq(1L), anyString());
        inOrder.verify(orderCommandService).persistOrder(
                eq("O202603250002"),
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
                eq(List.of(line))
        );
    }

    @Test
    void shouldRecordRemoteSucceededCreateCommandWhenFinalLocalPersistFails() {
        OrderCreateWorkflowService workflowService = new OrderCreateWorkflowService(
                orderCommandService,
                orderCommandMapper,
                new ObjectMapper(),
                DIRECT_TX
        );
        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItem(1L, 11L, 2)), null, null);
        OrderCommandService.PreparedOrderLine line = new OrderCommandService.PreparedOrderLine(
                1L,
                "P-1",
                11L,
                "SKU-11",
                "黑色 L",
                "[]",
                2000L,
                2,
                4000L,
                2001L,
                true,
                true
        );
        when(orderCommandService.orderMetrics()).thenReturn(new OrderMetrics(new SimpleMeterRegistry()));
        when(orderCommandService.orderProperties()).thenReturn(new OrderProperties());
        when(orderCommandService.requireIdempotencyKey("idem-3")).thenReturn("idem-3");
        when(orderCommandService.normalizeSource(null)).thenReturn(null);
        when(orderCommandService.normalizeConversationId(null, null)).thenReturn(null);
        when(orderCommandService.orderMapper()).thenReturn(orderMapper);
        when(orderCommandService.prepareOrderLines(1001L, List.of("ROLE_USER"), request)).thenReturn(List.of(line));
        when(orderMapper.findOrderNoByBuyerAndIdempotencyKey(1001L, "idem-3")).thenReturn(null);
        when(orderCommandService.generateOrderNo()).thenReturn("O202603250003");
        doAnswer(invocation -> {
            OrderCommandEntity entity = invocation.getArgument(0);
            entity.setId(2L);
            return 1;
        }).when(orderCommandMapper).insertOrderCommand(any(OrderCommandEntity.class));
        when(orderCommandMapper.findByCommandNo(anyString())).thenReturn(commandRecord("PREPARED", "O202603250003", 2L));
        when(orderCommandService.productServiceClient()).thenReturn(productServiceClient);
        when(orderCommandService.stockBizNo("O202603250003", 11L)).thenReturn("O202603250003:11");
        when(productServiceClient.deductStock(11L, 2, "O202603250003:11", 1001L, List.of("ROLE_USER")))
                .thenReturn(new StockOperateSnapshot(true, false, "O202603250003:11", 11L, 2, 98));
        doThrow(new IllegalStateException("persist failed")).when(orderCommandService).persistOrder(
                eq("O202603250003"),
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
                eq(List.of(line))
        );

        assertThatThrownBy(() -> workflowService.createOrder(1001L, List.of("ROLE_USER"), "idem-3", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persist failed");

        verify(orderCommandMapper).markRemoteSucceeded(eq(2L), anyString());
        verify(productServiceClient, never()).releaseStock(anyLong(), any(), anyString(), anyLong(), anyList());
    }

    private OrderCommandRecord commandRecord(String status) {
        return commandRecord(status, "O202603250001", 1L);
    }

    private OrderCommandRecord commandRecord(String status, String orderNo, Long id) {
        return new OrderCommandRecord(
                id,
                "CMD-001",
                "CREATE_ORDER",
                1001L,
                "idem-1",
                orderNo,
                status,
                "{\"buyerUserId\":1001}",
                "{\"preparedLines\":[],\"deductedLines\":[]}",
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
