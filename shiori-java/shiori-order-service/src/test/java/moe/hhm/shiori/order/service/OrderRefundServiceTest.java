package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import moe.hhm.shiori.order.client.PaymentServiceClient;
import moe.hhm.shiori.order.client.RefundBalancePaymentSnapshot;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.dto.OrderRefundResponse;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.model.OrderRefundEntity;
import moe.hhm.shiori.order.model.OrderRefundRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderRefundServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private PaymentServiceClient paymentServiceClient;

    private OrderRefundService orderRefundService;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties();
        orderRefundService = new OrderRefundService(orderMapper, paymentServiceClient, orderProperties);
    }

    @Test
    void shouldApplyRefundAsRequestedWhenOrderDelivering() {
        when(orderMapper.findOrderByOrderNo("O1001"))
                .thenReturn(order("O1001", 1001L, 2001L, 4, 888L));
        when(orderMapper.findPaymentModeByOrderNo("O1001")).thenReturn("BALANCE_ESCROW");
        when(orderMapper.findOrderRefundByOrderNoForUpdate("O1001")).thenReturn(null);
        when(orderMapper.findOrderRefundByRefundNo(anyString())).thenAnswer(invocation -> {
            String refundNo = invocation.getArgument(0);
            return refund(refundNo, "O1001", 1001L, 2001L, "REQUESTED");
        });

        OrderRefundResponse response = orderRefundService.applyRefund(1001L, "O1001", "要退款");

        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.idempotent()).isFalse();
        verify(paymentServiceClient, never()).refundOrderPayment(anyString(), anyString(), anyString(), any(), any(),
                anyLong(), any());
    }

    @Test
    void shouldApproveRefundToPendingFundsWhenSellerBalanceNotEnough() {
        when(orderMapper.findOrderRefundByRefundNoForUpdate("R2001"))
                .thenReturn(refund("R2001", "O2001", 1001L, 2001L, "REQUESTED"));
        when(paymentServiceClient.refundOrderPayment(eq("O2001"), eq("R2001"), eq("SELLER"), eq(2001L), anyString(),
                eq(2001L), any()))
                .thenReturn(new RefundBalancePaymentSnapshot("O2001", "R2001", "P2001", "PENDING_FUNDS", false));
        when(orderMapper.markOrderRefundReviewed(eq("R2001"), eq("REQUESTED"), eq("PENDING_FUNDS"), eq(2001L), any(),
                eq(0), eq("P2001"), eq("SELLER_BALANCE_NOT_ENOUGH")))
                .thenReturn(1);
        when(orderMapper.findOrderRefundByRefundNo("R2001"))
                .thenReturn(refund("R2001", "O2001", 1001L, 2001L, "PENDING_FUNDS"));

        OrderRefundResponse response = orderRefundService.approveRefundAsSeller(2001L, "R2001", "同意");

        assertThat(response.status()).isEqualTo("PENDING_FUNDS");
        assertThat(response.idempotent()).isFalse();
    }

    @Test
    void shouldRetryPendingRefundAsAdminToSucceeded() {
        when(orderMapper.findOrderRefundByRefundNoForUpdate("R3001"))
                .thenReturn(refund("R3001", "O3001", 1001L, 2001L, "PENDING_FUNDS"));
        when(paymentServiceClient.refundOrderPayment(eq("O3001"), eq("R3001"), eq("ADMIN"), eq(9001L), anyString(),
                eq(9001L), any()))
                .thenReturn(new RefundBalancePaymentSnapshot("O3001", "R3001", "P3001", "SUCCEEDED", false));
        when(orderMapper.updateOrderRefundAfterRetry(eq("R3001"), eq("PENDING_FUNDS"), eq("SUCCEEDED"),
                eq("P3001"), eq(null), eq(1)))
                .thenReturn(1);
        when(orderMapper.findOrderRefundByRefundNo("R3001"))
                .thenReturn(refund("R3001", "O3001", 1001L, 2001L, "SUCCEEDED"));

        OrderRefundResponse response = orderRefundService.retryRefundAsAdmin(9001L, "R3001");

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.idempotent()).isFalse();
    }

    @Test
    void shouldReturnIdempotentWhenApplyRefundDuplicated() {
        when(orderMapper.findOrderByOrderNo("O4001"))
                .thenReturn(order("O4001", 1001L, 2001L, 2, 777L));
        when(orderMapper.findPaymentModeByOrderNo("O4001")).thenReturn("BALANCE_ESCROW");
        when(orderMapper.findOrderRefundByOrderNoForUpdate("O4001"))
                .thenReturn(refund("R4001", "O4001", 1001L, 2001L, "REQUESTED"));

        OrderRefundResponse response = orderRefundService.applyRefund(1001L, "O4001", "again");

        assertThat(response.refundNo()).isEqualTo("R4001");
        assertThat(response.idempotent()).isTrue();
    }

    @Test
    void shouldCreateRefundNoWhenApplyRefund() {
        when(orderMapper.findOrderByOrderNo("O5001"))
                .thenReturn(order("O5001", 1001L, 2001L, 4, 555L));
        when(orderMapper.findPaymentModeByOrderNo("O5001")).thenReturn("BALANCE_ESCROW");
        when(orderMapper.findOrderRefundByOrderNoForUpdate("O5001")).thenReturn(null);
        when(orderMapper.findOrderRefundByRefundNo(anyString())).thenAnswer(invocation -> {
            String refundNo = invocation.getArgument(0);
            return refund(refundNo, "O5001", 1001L, 2001L, "REQUESTED");
        });

        orderRefundService.applyRefund(1001L, "O5001", null);

        ArgumentCaptor<OrderRefundEntity> captor = ArgumentCaptor.forClass(OrderRefundEntity.class);
        verify(orderMapper).insertOrderRefund(captor.capture());
        assertThat(captor.getValue().getRefundNo()).startsWith("R");
    }

    private OrderRecord order(String orderNo, Long buyerUserId, Long sellerUserId, Integer status, Long amountCent) {
        return new OrderRecord(
                1L,
                orderNo,
                buyerUserId,
                sellerUserId,
                status,
                amountCent,
                1,
                "P-TEST",
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                0,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private OrderRefundRecord refund(String refundNo, String orderNo, Long buyerUserId, Long sellerUserId, String status) {
        return new OrderRefundRecord(
                1L,
                refundNo,
                orderNo,
                buyerUserId,
                sellerUserId,
                100L,
                status,
                "reason",
                null,
                null,
                LocalDateTime.now().plusHours(1),
                null,
                0,
                "P-TEST",
                null,
                0,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
