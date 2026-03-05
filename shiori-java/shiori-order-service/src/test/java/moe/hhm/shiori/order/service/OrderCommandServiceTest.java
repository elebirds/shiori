package moe.hhm.shiori.order.service;

import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.client.ProductDetailSnapshot;
import moe.hhm.shiori.order.client.ProductServiceClient;
import moe.hhm.shiori.order.client.ProductSkuSnapshot;
import moe.hhm.shiori.order.client.NotifyChatClient;
import moe.hhm.shiori.order.client.PaymentServiceClient;
import moe.hhm.shiori.order.client.ReserveBalancePaymentSnapshot;
import moe.hhm.shiori.order.client.SettleBalancePaymentSnapshot;
import moe.hhm.shiori.order.config.OrderMqProperties;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.dto.CreateOrderItem;
import moe.hhm.shiori.order.dto.CreateOrderRequest;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.model.OrderOperateIdempotencyRecord;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private NotifyChatClient notifyChatClient;
    @Mock
    private PaymentServiceClient paymentServiceClient;

    private OrderCommandService orderCommandService;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties();
        OrderMqProperties orderMqProperties = new OrderMqProperties();
        orderCommandService = new OrderCommandService(
                orderMapper,
                productServiceClient,
                notifyChatClient,
                paymentServiceClient,
                orderProperties,
                orderMqProperties,
                new ObjectMapper(),
                new OrderMetrics(new SimpleMeterRegistry())
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
                .thenReturn(new OrderRecord(
                        1L, "O202603030001", 1001L, 2001L, 1, 999L, 1,
                        null, null, null, null, null, null, null, null, 0, null, null
                ));

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
                .thenReturn(new OrderRecord(
                        9L, "O202603030009", 1001L, 2001L, 2, 1999L, 2,
                        "PAY-001", null, null, null, null, null, null, null, 0, null, null
                ));

        OrderOperateResponse response = orderCommandService.payOrder(1001L, "O202603030009", "PAY-001", "idem-pay-1");
        assertThat(response.idempotent()).isTrue();
        assertThat(response.status()).isEqualTo("PAID");
        verify(orderMapper, never()).markOrderPaid(anyString(), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void shouldDeliverOrderAsSeller() {
        when(orderMapper.findOrderByOrderNo("O202603040001"))
                .thenReturn(new OrderRecord(
                        10L, "O202603040001", 1001L, 2001L, 2, 1999L, 1,
                        "PAY-1", null, null, null, null, null, null, null, 0, null, null
                ));
        when(orderMapper.markOrderDeliveringBySeller("O202603040001", 2001L, 2, 4)).thenReturn(1);

        OrderOperateResponse response = orderCommandService.deliverOrderAsSeller(2001L, "O202603040001", "ship");

        assertThat(response.idempotent()).isFalse();
        assertThat(response.status()).isEqualTo("DELIVERING");
        verify(orderMapper).insertStatusAuditLog("O202603040001", 2001L, "SELLER", 2, 4, "ship");
    }

    @Test
    void shouldRejectFinishWhenStatusInvalidForSeller() {
        when(orderMapper.findOrderByOrderNo("O202603040002"))
                .thenReturn(new OrderRecord(
                        11L, "O202603040002", 1001L, 2001L, 2, 2999L, 1,
                        "PAY-2", null, null, null, null, null, null, null, 0, null, null
                ));

        assertThatThrownBy(() -> orderCommandService.finishOrderAsSeller(2001L, "O202603040002", "done"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50004);

        verify(orderMapper, never()).markOrderFinishedBySeller(anyString(), anyLong(), any(), any());
    }

    @Test
    void shouldFinishOrderAsAdmin() {
        when(orderMapper.findOrderByOrderNo("O202603040003"))
                .thenReturn(
                        new OrderRecord(12L, "O202603040003", 1001L, 2001L, 4, 3999L, 1,
                                "PAY-3", null, null, null, null, null, null, null, 0, null, null),
                        new OrderRecord(12L, "O202603040003", 1001L, 2001L, 5, 3999L, 1,
                                "PAY-3", null, null, null, null, null, null, null, 0, null, null)
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
                .thenReturn(new OrderRecord(
                        13L, "O202603040004", 1001L, 2001L, 1, 1999L, 1,
                        null, null, null, null, null, null, null, null, 0, null, null
                ));
        when(orderMapper.findOperateIdempotency(1001L, "PAY", "idem-pay-conflict"))
                .thenReturn(new OrderOperateIdempotencyRecord(1001L, "PAY", "idem-pay-conflict", "O-OTHER"));

        assertThatThrownBy(() -> orderCommandService.payOrder(1001L, "O202603040004", "PAY-004", "idem-pay-conflict"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50016);
    }

    @Test
    void shouldPayOrderByBalance() {
        when(orderMapper.findOrderByOrderNo("O202603050001"))
                .thenReturn(new OrderRecord(
                        14L, "O202603050001", 1001L, 2001L, 1, 2399L, 1,
                        null, null, null, null, null, null, null, null, 0, null, null
                ));
        when(orderMapper.findOperateIdempotency(1001L, "PAY", "idem-balance-pay-1")).thenReturn(null);
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
    void shouldSettleBalanceEscrowWhenBuyerConfirmReceipt() {
        when(orderMapper.findOrderByOrderNo("O202603050002"))
                .thenReturn(new OrderRecord(
                        15L, "O202603050002", 1001L, 2001L, 4, 3999L, 1,
                        "P-002", null, null, null, null, null, null, null, 0, null, null
                ));
        when(orderMapper.markOrderFinishedByBuyer("O202603050002", 1001L, 4, 5)).thenReturn(1);
        when(orderMapper.findPaymentModeByOrderNo("O202603050002")).thenReturn("BALANCE_ESCROW");
        when(paymentServiceClient.settleOrderPayment("O202603050002", "BUYER", 1001L, 1001L, List.of("ROLE_USER")))
                .thenReturn(new SettleBalancePaymentSnapshot("O202603050002", "P-002", "SETTLED", false));

        OrderOperateResponse response = orderCommandService.confirmReceiptAsBuyer(1001L, "O202603050002", null);

        assertThat(response.idempotent()).isFalse();
        assertThat(response.status()).isEqualTo("FINISHED");
        verify(paymentServiceClient).settleOrderPayment("O202603050002", "BUYER", 1001L, 1001L, List.of("ROLE_USER"));
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
}
