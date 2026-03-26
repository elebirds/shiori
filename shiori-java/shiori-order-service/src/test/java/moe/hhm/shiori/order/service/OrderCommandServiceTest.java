package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.client.ProductDetailSnapshot;
import moe.hhm.shiori.order.client.ProductServiceClient;
import moe.hhm.shiori.order.client.ProductSkuSnapshot;
import moe.hhm.shiori.order.client.NotifyChatClient;
import moe.hhm.shiori.order.client.PaymentServiceClient;
import moe.hhm.shiori.order.client.UserAddressSnapshot;
import moe.hhm.shiori.order.client.UserServiceClient;
import moe.hhm.shiori.order.client.ReserveBalancePaymentSnapshot;
import moe.hhm.shiori.order.client.SettleBalancePaymentSnapshot;
import moe.hhm.shiori.order.config.OrderMqProperties;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.dto.CreateOrderItem;
import moe.hhm.shiori.order.dto.CreateOrderRequest;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
import moe.hhm.shiori.order.model.OrderCommandEntity;
import moe.hhm.shiori.order.model.OrderCommandRecord;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.dto.v2.UpdateOrderFulfillmentRequest;
import moe.hhm.shiori.order.model.OrderOperateIdempotencyRecord;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.repository.OrderCommandMapper;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    private static final TransactionOperations DIRECT_TX = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderCommandMapper orderCommandMapper;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private NotifyChatClient notifyChatClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;
    @Mock
    private ObjectProvider<OrderCreateWorkflowService> orderCreateWorkflowServiceProvider;
    @Mock
    private ObjectProvider<OrderPayWorkflowService> orderPayWorkflowServiceProvider;
    @Mock
    private ObjectProvider<OrderConfirmSettlementWorkflowService> orderConfirmSettlementWorkflowServiceProvider;

    private OrderCommandService orderCommandService;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties();
        OrderMqProperties orderMqProperties = new OrderMqProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        OrderMetrics orderMetrics = new OrderMetrics(new SimpleMeterRegistry());
        orderCommandService = new OrderCommandService(
                orderMapper,
                productServiceClient,
                userServiceClient,
                notifyChatClient,
                paymentServiceClient,
                orderProperties,
                orderMqProperties,
                objectMapper,
                orderMetrics,
                orderCreateWorkflowServiceProvider,
                orderPayWorkflowServiceProvider,
                orderConfirmSettlementWorkflowServiceProvider
        );
        lenient().when(orderCreateWorkflowServiceProvider.getObject()).thenReturn(
                new OrderCreateWorkflowService(orderCommandService, orderCommandMapper, objectMapper, DIRECT_TX)
        );
        lenient().when(orderPayWorkflowServiceProvider.getObject()).thenReturn(
                new OrderPayWorkflowService(orderCommandService, orderCommandMapper, objectMapper, DIRECT_TX)
        );
        lenient().when(orderConfirmSettlementWorkflowServiceProvider.getObject()).thenReturn(
                new OrderConfirmSettlementWorkflowService(orderCommandService, orderCommandMapper, objectMapper, DIRECT_TX)
        );
    }

    @Test
    void shouldRejectCrossSellerOrder() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new CreateOrderItem(1L, 11L, 1),
                new CreateOrderItem(2L, 22L, 1)
        ), null, null);
        when(orderMapper.findOrderNoByBuyerAndIdempotencyKey(1001L, "idem-1")).thenReturn(null);
        when(productServiceClient.getProductDetail(1L, 1001L, List.of("ROLE_USER")))
                .thenReturn(product(1L, "P1", 2001L, 11L, "S11", 1200L));
        when(productServiceClient.getProductDetail(2L, 1001L, List.of("ROLE_USER")))
                .thenReturn(product(2L, "P2", 2002L, 22L, "S22", 1300L));

        BizException ex;
        try {
            orderCommandService.createOrder(1001L, List.of("ROLE_USER"), "idem-1", request);
            throw new AssertionError("expected BizException");
        } catch (BizException actual) {
            ex = actual;
        }
        assertThat(ex.getErrorCode().code()).isEqualTo(50003);
        verify(productServiceClient, never()).deductStock(anyLong(), any(), anyString(), anyLong(), anyList());
    }

    @Test
    void shouldRejectSelfPurchase() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new CreateOrderItem(1L, 11L, 1)
        ), null, null);
        when(orderMapper.findOrderNoByBuyerAndIdempotencyKey(1001L, "idem-self")).thenReturn(null);
        when(productServiceClient.getProductDetail(1L, 1001L, List.of("ROLE_USER")))
                .thenReturn(product(1L, "P1", 1001L, 11L, "S11", 1200L));

        BizException ex;
        try {
            orderCommandService.createOrder(1001L, List.of("ROLE_USER"), "idem-self", request);
            throw new AssertionError("expected BizException");
        } catch (BizException actual) {
            ex = actual;
        }

        assertThat(ex.getErrorCode().code()).isEqualTo(50015);
        verify(productServiceClient, never()).deductStock(anyLong(), any(), anyString(), anyLong(), anyList());
    }

    @Test
    void shouldReturnIdempotentCreateResponseWhenOrderExists() {
        when(orderMapper.findOrderNoByBuyerAndIdempotencyKey(1001L, "idem-2")).thenReturn("O202603030001");
        when(orderMapper.findOrderByOrderNo("O202603030001"))
                .thenReturn(orderRecord(1L, "O202603030001", 1001L, 2001L, 1, 999L, 1, null));

        CreateOrderResponse response = orderCommandService.createOrder(
                1001L,
                List.of("ROLE_USER"),
                "idem-2",
                new CreateOrderRequest(List.of(new CreateOrderItem(1L, 11L, 1)), null, null)
        );

        assertThat(response.idempotent()).isTrue();
        assertThat(response.orderNo()).isEqualTo("O202603030001");
        verify(productServiceClient, never()).getProductDetail(anyLong(), any(), anyList());
    }

    @Test
    void shouldReturnIdempotentPayWhenAlreadyPaid() {
        when(orderMapper.findOrderByOrderNo("O202603030009"))
                .thenReturn(orderRecord(9L, "O202603030009", 1001L, 2001L, 2, 1999L, 2, "PAY-001"));

        OrderOperateResponse response = orderCommandService.payOrder(1001L, "O202603030009", "PAY-001", "idem-pay-1");
        assertThat(response.idempotent()).isTrue();
        assertThat(response.status()).isEqualTo("PAID");
        verify(orderMapper, never()).markOrderPaid(anyString(), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void shouldDeliverOrderAsSeller() {
        when(orderMapper.findOrderByOrderNo("O202603040001"))
                .thenReturn(orderRecord(10L, "O202603040001", 1001L, 2001L, 2, 1999L, 1, "PAY-1"));
        when(orderMapper.markOrderDeliveringBySeller("O202603040001", 2001L, 2, 4)).thenReturn(1);

        OrderOperateResponse response = orderCommandService.deliverOrderAsSeller(2001L, "O202603040001", "ship");

        assertThat(response.idempotent()).isFalse();
        assertThat(response.status()).isEqualTo("DELIVERING");
        verify(orderMapper).insertStatusAuditLog("O202603040001", 2001L, "SELLER", 2, 4, "ship");
        verify(orderMapper).insertOutboxEvent(argThat(entity ->
                "order".equals(entity.getAggregateType())
                        && "O202603040001".equals(entity.getAggregateId())
                        && "O202603040001".equals(entity.getMessageKey())
                        && "OrderDelivered".equals(entity.getType())
        ));
    }

    @Test
    void shouldRejectDeliverWhenRefundAlreadySucceeded() {
        when(orderMapper.findOrderByOrderNo("O202603040001"))
                .thenReturn(orderRecordWithRefund(10L, "O202603040001", 1001L, 2001L, 2, 1999L, 1, "PAY-1", "SUCCEEDED"));

        assertThatThrownBy(() -> orderCommandService.deliverOrderAsSeller(2001L, "O202603040001", "ship"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50004);
        verify(orderMapper, never()).markOrderDeliveringBySeller(anyString(), anyLong(), any(), any());
    }

    @Test
    void shouldRejectFinishWhenStatusInvalidForSeller() {
        when(orderMapper.findOrderByOrderNo("O202603040002"))
                .thenReturn(orderRecord(11L, "O202603040002", 1001L, 2001L, 2, 2999L, 1, "PAY-2"));

        assertThatThrownBy(() -> orderCommandService.finishOrderAsSeller(2001L, "O202603040002", "done"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50004);

        verify(orderMapper, never()).markOrderFinishedBySeller(anyString(), anyLong(), any(), any());
    }

    @Test
    void shouldFinishOrderAsAdmin() {
        when(orderMapper.findOrderByOrderNo("O202603040003"))
                .thenReturn(
                        orderRecord(12L, "O202603040003", 1001L, 2001L, 4, 3999L, 1, "PAY-3"),
                        orderRecord(12L, "O202603040003", 1001L, 2001L, 5, 3999L, 1, "PAY-3")
                );
        when(orderMapper.markOrderFinishedAsAdmin("O202603040003", 4, 5)).thenReturn(1);

        OrderOperateResponse response = orderCommandService.finishOrderAsAdmin(9001L, "O202603040003", "close");

        assertThat(response.idempotent()).isFalse();
        assertThat(response.status()).isEqualTo("FINISHED");
        verify(orderMapper).insertStatusAuditLog("O202603040003", 9001L, "ADMIN", 4, 5, "close");
        verify(orderMapper).insertAdminAuditLog(eq(9001L), eq("O202603040003"), eq("ORDER_ADMIN_FINISH"),
                anyString(), anyString(), eq("close"));
    }

    @Test
    void shouldRejectPayWhenIdempotencyKeyConflictsOrder() {
        when(orderMapper.findOrderByOrderNo("O202603040004"))
                .thenReturn(orderRecord(13L, "O202603040004", 1001L, 2001L, 1, 1999L, 1, null));
        when(orderMapper.findOperateIdempotency(1001L, "PAY", "idem-pay-conflict"))
                .thenReturn(new OrderOperateIdempotencyRecord(1001L, "PAY", "idem-pay-conflict", "O-OTHER"));

        assertThatThrownBy(() -> orderCommandService.payOrder(1001L, "O202603040004", "PAY-004", "idem-pay-conflict"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50016);
    }

    @Test
    void shouldPayOrderByBalance() {
        OrderRecord order = orderRecord(14L, "O202603050001", 1001L, 2001L, 1, 2399L, 1, null);
        when(orderMapper.findOrderByOrderNo("O202603050001")).thenReturn(order);
        when(orderMapper.findOrderByOrderNoForUpdate("O202603050001")).thenReturn(order);
        when(orderMapper.findOperateIdempotency(1001L, "PAY", "idem-balance-pay-1")).thenReturn(null);
        doAnswer(invocation -> {
            OrderCommandEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(orderCommandMapper).insertOrderCommand(any(OrderCommandEntity.class));
        when(orderCommandMapper.findByCommandNo(anyString())).thenReturn(new OrderCommandRecord(
                1L,
                "CMD-PAY-1",
                "PAY_BALANCE_ORDER",
                1001L,
                "idem-balance-pay-1",
                "O202603050001",
                "PREPARED",
                "{\"buyerUserId\":1001}",
                "{\"paymentNo\":null}",
                null,
                null,
                0,
                null,
                null,
                LocalDateTime.now().minusSeconds(10),
                LocalDateTime.now().minusSeconds(5)
        ));
        when(paymentServiceClient.reserveOrderPayment("O202603050001", 1001L, 2001L, 2399L, 1001L, List.of("ROLE_USER")))
                .thenReturn(new ReserveBalancePaymentSnapshot("O202603050001", "P-001", "RESERVED", false));
        when(orderMapper.markOrderPaidByBalance(eq("O202603050001"), eq(1001L), eq("P-001"), any(), eq(1), eq(2)))
                .thenReturn(1);

        OrderOperateResponse response =
                orderCommandService.payOrderByBalance(1001L, "O202603050001", "idem-balance-pay-1");

        assertThat(response.idempotent()).isFalse();
        assertThat(response.status()).isEqualTo("PAID");
        verify(paymentServiceClient).reserveOrderPayment("O202603050001", 1001L, 2001L, 2399L, 1001L, List.of("ROLE_USER"));
        verify(orderMapper).markOrderPaidByBalance(eq("O202603050001"), eq(1001L), eq("P-001"), any(), eq(1), eq(2));
    }

    @Test
    void shouldScheduleBalanceEscrowSettlementWhenBuyerConfirmReceipt() {
        when(orderMapper.findOrderByOrderNo("O202603050002"))
                .thenReturn(orderRecord(15L, "O202603050002", 1001L, 2001L, 4, 3999L, 1, "P-002"));
        when(orderMapper.markOrderFinishedByBuyer("O202603050002", 1001L, 4, 5)).thenReturn(1);
        when(orderMapper.findPaymentModeByOrderNo("O202603050002")).thenReturn("BALANCE_ESCROW");
        doAnswer(invocation -> {
            OrderCommandEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            return 1;
        }).when(orderCommandMapper).insertOrderCommand(any(OrderCommandEntity.class));

        OrderOperateResponse response = orderCommandService.confirmReceiptAsBuyer(1001L, "O202603050002", null);

        assertThat(response.idempotent()).isFalse();
        assertThat(response.status()).isEqualTo("FINISHED");
        verify(orderCommandMapper).insertOrderCommand(any(OrderCommandEntity.class));
        verify(paymentServiceClient, never()).settleOrderPayment(anyString(), anyString(), any(), any(), anyList());
    }

    @Test
    void shouldUpdateOrderFulfillmentToDelivery() {
        when(orderMapper.findOrderByOrderNo("O202603060001"))
                .thenReturn(orderRecordWithFulfillment(
                        16L,
                        "O202603060001",
                        1001L,
                        2001L,
                        1,
                        2399L,
                        1,
                        null,
                        1,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
        when(userServiceClient.getMyAddress(101L, 1001L, List.of("ROLE_USER")))
                .thenReturn(new UserAddressSnapshot(
                        101L,
                        1001L,
                        "张三",
                        "13800138000",
                        "广东省",
                        "深圳市",
                        "南山区",
                        "科技园 1 号",
                        true
                ));
        when(orderMapper.updateOrderFulfillmentToDelivery(
                "O202603060001",
                1001L,
                1,
                101L,
                "张三",
                "13800138000",
                "广东省",
                "深圳市",
                "南山区",
                "科技园 1 号"
        )).thenReturn(1);

        OrderOperateResponse response = orderCommandService.updateOrderFulfillmentByBuyer(
                1001L,
                List.of("ROLE_USER"),
                "O202603060001",
                new UpdateOrderFulfillmentRequest("DELIVERY", 101L)
        );

        assertThat(response.status()).isEqualTo("UNPAID");
        verify(orderMapper).updateOrderFulfillmentToDelivery(
                "O202603060001",
                1001L,
                1,
                101L,
                "张三",
                "13800138000",
                "广东省",
                "深圳市",
                "南山区",
                "科技园 1 号"
        );
    }

    @Test
    void shouldRejectBalancePayWhenDeliveryAddressSnapshotMissing() {
        when(orderMapper.findOrderByOrderNo("O202603060002"))
                .thenReturn(orderRecordWithFulfillment(
                        17L,
                        "O202603060002",
                        1001L,
                        2001L,
                        1,
                        3299L,
                        1,
                        null,
                        0,
                        1,
                        "DELIVERY",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
        when(orderMapper.findOperateIdempotency(1001L, "PAY", "idem-balance-pay-2")).thenReturn(null);

        assertThatThrownBy(() -> orderCommandService.payOrderByBalance(1001L, "O202603060002", "idem-balance-pay-2"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50032);

        verify(paymentServiceClient, never()).reserveOrderPayment(anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyList());
    }

    @Test
    void shouldRejectConfirmReceiptWhenRefundAlreadySucceeded() {
        when(orderMapper.findOrderByOrderNo("O202603050002"))
                .thenReturn(orderRecordWithRefund(15L, "O202603050002", 1001L, 2001L, 4, 3999L, 1, "P-002", "SUCCEEDED"));

        assertThatThrownBy(() -> orderCommandService.confirmReceiptAsBuyer(1001L, "O202603050002", null))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50004);
        verify(orderMapper, never()).markOrderFinishedByBuyer(anyString(), anyLong(), any(), any());
        verify(paymentServiceClient, never()).settleOrderPayment(anyString(), anyString(), any(), any(), anyList());
    }

    @Test
    void shouldGenerateUniqueOrderNosUnderBurstConcurrency() throws Exception {
        int threads = 32;
        int perThread = 5000;
        Set<String> orderNos = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < threads; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("await interrupted", ex);
                    }
                    for (int i = 0; i < perThread; i++) {
                        orderNos.add(orderCommandService.generateOrderNo());
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(orderNos).hasSize(threads * perThread);
        assertThat(orderNos).allMatch(orderNo -> orderNo.startsWith("O"));
    }

    private ProductDetailSnapshot product(Long productId, String productNo, Long ownerUserId,
                                          Long skuId, String skuNo, Long priceCent) {
        return new ProductDetailSnapshot(
                productId,
                productNo,
                ownerUserId,
                "title",
                "desc",
                "ON_SALE",
                List.of(new ProductSkuSnapshot(skuId, skuNo, "sku", "{}", priceCent, 100))
        );
    }

    private OrderRecord orderRecord(Long id,
                                    String orderNo,
                                    Long buyerUserId,
                                    Long sellerUserId,
                                    Integer status,
                                    Long totalAmountCent,
                                    Integer itemCount,
                                    String paymentNo) {
        return new OrderRecord(
                id,
                orderNo,
                buyerUserId,
                sellerUserId,
                status,
                totalAmountCent,
                itemCount,
                paymentNo,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                0,
                "MEETUP",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null
        );
    }

    private OrderRecord orderRecordWithRefund(Long id,
                                              String orderNo,
                                              Long buyerUserId,
                                              Long sellerUserId,
                                              Integer status,
                                              Long totalAmountCent,
                                              Integer itemCount,
                                              String paymentNo,
                                              String refundStatus) {
        return new OrderRecord(
                id,
                orderNo,
                buyerUserId,
                sellerUserId,
                status,
                totalAmountCent,
                itemCount,
                paymentNo,
                refundStatus,
                "R-TEST",
                totalAmountCent,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                0,
                "MEETUP",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null
        );
    }

    private OrderRecord orderRecordWithFulfillment(Long id,
                                                   String orderNo,
                                                   Long buyerUserId,
                                                   Long sellerUserId,
                                                   Integer status,
                                                   Long totalAmountCent,
                                                   Integer itemCount,
                                                   String paymentNo,
                                                   Integer allowMeetup,
                                                   Integer allowDelivery,
                                                   String fulfillmentMode,
                                                   Long shippingAddressId,
                                                   String shippingReceiverName,
                                                   String shippingReceiverPhone,
                                                   String shippingProvince,
                                                   String shippingCity,
                                                   String shippingDistrict) {
        return new OrderRecord(
                id,
                orderNo,
                buyerUserId,
                sellerUserId,
                status,
                totalAmountCent,
                itemCount,
                paymentNo,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                allowMeetup,
                allowDelivery,
                fulfillmentMode,
                shippingAddressId,
                shippingReceiverName,
                shippingReceiverPhone,
                shippingProvince,
                shippingCity,
                shippingDistrict,
                null,
                0,
                null,
                null
        );
    }
}
