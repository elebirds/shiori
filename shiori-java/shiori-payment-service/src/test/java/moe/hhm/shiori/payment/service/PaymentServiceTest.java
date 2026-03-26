package moe.hhm.shiori.payment.service;

import java.time.LocalDateTime;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.payment.domain.CdkCodeStatus;
import moe.hhm.shiori.payment.domain.TradePaymentStatus;
import moe.hhm.shiori.payment.dto.RedeemCdkResponse;
import moe.hhm.shiori.payment.dto.admin.CreateCdkBatchRequest;
import moe.hhm.shiori.payment.dto.admin.CreateCdkBatchResponse;
import moe.hhm.shiori.payment.dto.internal.InitWalletAccountResponse;
import moe.hhm.shiori.payment.dto.internal.ReleaseOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.ReserveOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.SettleOrderPaymentResponse;
import moe.hhm.shiori.payment.model.CdkCodeRecord;
import moe.hhm.shiori.payment.model.TradePaymentRecord;
import moe.hhm.shiori.payment.model.WalletAccountRecord;
import moe.hhm.shiori.payment.repository.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentMapper paymentMapper;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentMapper, new ObjectMapper());
    }

    @Test
    void shouldRedeemCdkSuccessfully() {
        when(paymentMapper.findCdkByHashForUpdate(anyString())).thenReturn(
                new CdkCodeRecord(1L, 1L, "hash", "CDK****ABCD", 200L, null, CdkCodeStatus.UNUSED.code(), null, null)
        );
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 500L, 10L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 500L, 10L));
        when(paymentMapper.markCdkRedeemed(eq(1L), eq(1001L), any(), eq(CdkCodeStatus.UNUSED.code()), eq(CdkCodeStatus.USED.code())))
                .thenReturn(1);

        RedeemCdkResponse response = paymentService.redeemCdk(1001L, " cdk-test ");

        assertThat(response.redeemAmountCent()).isEqualTo(200L);
        assertThat(response.availableBalanceCent()).isEqualTo(700L);
        assertThat(response.frozenBalanceCent()).isEqualTo(10L);
        verify(paymentMapper).updateWalletBalance(1001L, 700L, 10L);
    }

    @Test
    void shouldRejectRedeemWhenCdkInvalid() {
        when(paymentMapper.findCdkByHashForUpdate(anyString())).thenReturn(null);
        assertThatThrownBy(() -> paymentService.redeemCdk(1001L, "missing"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 60004);
    }

    @Test
    void shouldRejectRedeemWhenCdkAlreadyUsed() {
        when(paymentMapper.findCdkByHashForUpdate(anyString())).thenReturn(
                new CdkCodeRecord(2L, 1L, "hash", "CDK****USED", 200L, null, CdkCodeStatus.USED.code(), 1002L, LocalDateTime.now())
        );
        assertThatThrownBy(() -> paymentService.redeemCdk(1001L, "used"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 60005);
    }

    @Test
    void shouldRejectRedeemWhenCdkExpired() {
        when(paymentMapper.findCdkByHashForUpdate(anyString())).thenReturn(
                new CdkCodeRecord(3L, 1L, "hash", "CDK****OLD1", 200L, LocalDateTime.now().minusSeconds(1),
                        CdkCodeStatus.UNUSED.code(), null, null)
        );
        assertThatThrownBy(() -> paymentService.redeemCdk(1001L, "expired"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 60006);
    }

    @Test
    void shouldCreateCdkBatchWithMaskedCodes() {
        doAnswer(invocation -> {
            Object entity = invocation.getArgument(0);
            try {
                entity.getClass().getMethod("setId", Long.class).invoke(entity, 99L);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            return 1;
        }).when(paymentMapper).insertCdkBatch(any());

        CreateCdkBatchRequest request = new CreateCdkBatchRequest(5, 300L, LocalDateTime.now().plusDays(7));
        CreateCdkBatchResponse response = paymentService.createCdkBatch(9001L, request);

        assertThat(response.codes()).hasSize(5);
        assertThat(response.codes()).allSatisfy(item -> {
            assertThat(item.codeMask()).isNotBlank();
            assertThat(item.codeMask()).isNotEqualTo(item.code());
            assertThat(item.codeMask()).contains("****");
        });
    }

    @Test
    void shouldReserveOrderPaymentSuccessfully() {
        when(paymentMapper.findTradeByOrderNo("O1001")).thenReturn((TradePaymentRecord) null, (TradePaymentRecord) null);
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 500L, 10L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 500L, 10L));

        ReserveOrderPaymentResponse response = paymentService.reserveOrderPayment("O1001", 1001L, 2001L, 300L);

        assertThat(response.status()).isEqualTo("RESERVED");
        assertThat(response.idempotent()).isFalse();
        verify(paymentMapper).updateWalletBalance(1001L, 200L, 310L);
        verify(paymentMapper).insertTradePayment(eq("O1001"), anyString(), eq(1001L), eq(2001L), eq(300L),
                eq(TradePaymentStatus.RESERVED.code()), any());
        verify(paymentMapper).insertWalletBalanceOutbox(
                anyString(),
                eq("wallet"),
                eq("1001"),
                eq("1001"),
                eq(1001L),
                eq("O1001"),
                anyString(),
                eq("PENDING"),
                eq(0),
                eq(null),
                eq(null),
                eq(null)
        );
    }

    @Test
    void shouldRejectReserveWhenBalanceNotEnough() {
        when(paymentMapper.findTradeByOrderNo("O1002")).thenReturn((TradePaymentRecord) null, (TradePaymentRecord) null);
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 99L, 0L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 99L, 0L));

        assertThatThrownBy(() -> paymentService.reserveOrderPayment("O1002", 1001L, 2001L, 100L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 60000);
    }

    @Test
    void shouldReturnIdempotentWhenTradeAlreadyReserved() {
        when(paymentMapper.findTradeByOrderNo("O1003"))
                .thenReturn(trade("O1003", "P-1003", 1001L, 2001L, 100L, TradePaymentStatus.RESERVED.code()));

        ReserveOrderPaymentResponse response = paymentService.reserveOrderPayment("O1003", 1001L, 2001L, 100L);

        assertThat(response.idempotent()).isTrue();
        assertThat(response.paymentNo()).isEqualTo("P-1003");
        verify(paymentMapper, never()).updateWalletBalance(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRejectIdempotentReserveWhenRequestMismatch() {
        when(paymentMapper.findTradeByOrderNo("O1003"))
                .thenReturn(trade("O1003", "P-1003", 1001L, 2001L, 100L, TradePaymentStatus.RESERVED.code()));

        assertThatThrownBy(() -> paymentService.reserveOrderPayment("O1003", 1001L, 2001L, 101L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 60003);
        verify(paymentMapper, never()).updateWalletBalance(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldReturnIdempotentWhenTradeAppearsAfterWalletLocked() {
        when(paymentMapper.findTradeByOrderNo("O1004"))
                .thenReturn(null, trade("O1004", "P-1004", 1001L, 2001L, 100L, TradePaymentStatus.RESERVED.code()));
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 500L, 0L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 500L, 0L));

        ReserveOrderPaymentResponse response = paymentService.reserveOrderPayment("O1004", 1001L, 2001L, 100L);

        assertThat(response.idempotent()).isTrue();
        assertThat(response.status()).isEqualTo("RESERVED");
        verify(paymentMapper, never()).findTradeByOrderNoForUpdate("O1004");
        verify(paymentMapper, never()).updateWalletBalance(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRejectReserveWhenDuplicateTradeStatusSettled() {
        when(paymentMapper.findTradeByOrderNo("O1005"))
                .thenReturn(
                        null,
                        null,
                        trade("O1005", "P-1005", 1001L, 2001L, 100L, TradePaymentStatus.SETTLED.code())
                );
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 500L, 0L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 500L, 0L));
        when(paymentMapper.insertTradePayment(eq("O1005"), anyString(), eq(1001L), eq(2001L), eq(100L),
                eq(TradePaymentStatus.RESERVED.code()), any()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> paymentService.reserveOrderPayment("O1005", 1001L, 2001L, 100L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 60003);
    }

    @Test
    void shouldRejectReserveWhenDuplicateTradeReservedButRequestMismatch() {
        when(paymentMapper.findTradeByOrderNo("O1006"))
                .thenReturn(
                        null,
                        null,
                        trade("O1006", "P-1006", 1001L, 2001L, 101L, TradePaymentStatus.RESERVED.code())
                );
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 500L, 0L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 500L, 0L));
        when(paymentMapper.insertTradePayment(eq("O1006"), anyString(), eq(1001L), eq(2001L), eq(100L),
                eq(TradePaymentStatus.RESERVED.code()), any()))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> paymentService.reserveOrderPayment("O1006", 1001L, 2001L, 100L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 60003);
    }

    @Test
    void shouldLockWalletBeforeCheckingTradeGapDuringReserve() {
        when(paymentMapper.findTradeByOrderNo("O1007")).thenReturn((TradePaymentRecord) null, (TradePaymentRecord) null);
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 500L, 0L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 500L, 0L));

        paymentService.reserveOrderPayment("O1007", 1001L, 2001L, 100L);

        InOrder inOrder = inOrder(paymentMapper);
        inOrder.verify(paymentMapper).findTradeByOrderNo("O1007");
        inOrder.verify(paymentMapper).findWalletByUserId(1001L);
        inOrder.verify(paymentMapper).findWalletByUserIdForUpdate(1001L);
        inOrder.verify(paymentMapper).findTradeByOrderNo("O1007");
        verify(paymentMapper, never()).findTradeByOrderNoForUpdate("O1007");
    }

    @Test
    void shouldNotReadWalletAgainAfterReserveBalanceUpdated() {
        when(paymentMapper.findTradeByOrderNo("O1008")).thenReturn((TradePaymentRecord) null, (TradePaymentRecord) null);
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 500L, 0L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 500L, 0L));

        paymentService.reserveOrderPayment("O1008", 1001L, 2001L, 100L);

        verify(paymentMapper, never()).findTradeByOrderNoForUpdate("O1008");
        verify(paymentMapper, times(1)).findWalletByUserId(1001L);
    }

    @Test
    void shouldInitWalletAccountWhenMissing() {
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(null, wallet(1001L, 0L, 0L));

        InitWalletAccountResponse response = paymentService.initWalletAccount(1001L);

        assertThat(response.userId()).isEqualTo(1001L);
        assertThat(response.idempotent()).isFalse();
        verify(paymentMapper).insertWalletAccount(1001L, 0L, 0L);
    }

    @Test
    void shouldReturnIdempotentWhenWalletAccountAlreadyExists() {
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 0L, 0L));

        InitWalletAccountResponse response = paymentService.initWalletAccount(1001L);

        assertThat(response.userId()).isEqualTo(1001L);
        assertThat(response.idempotent()).isTrue();
        verify(paymentMapper, never()).insertWalletAccount(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldSettleOrderPaymentSuccessfully() {
        when(paymentMapper.findTradeByOrderNoForUpdate("O2001"))
                .thenReturn(trade("O2001", "P-2001", 1001L, 2001L, 150L, TradePaymentStatus.RESERVED.code()));
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 300L, 200L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 300L, 200L));
        when(paymentMapper.findWalletByUserId(2001L)).thenReturn(wallet(2001L, 50L, 0L));
        when(paymentMapper.findWalletByUserIdForUpdate(2001L)).thenReturn(wallet(2001L, 50L, 0L));
        when(paymentMapper.markTradeSettled(eq("O2001"), eq(TradePaymentStatus.RESERVED.code()),
                eq(TradePaymentStatus.SETTLED.code()), any())).thenReturn(1);

        SettleOrderPaymentResponse response = paymentService.settleOrderPayment("O2001", "BUYER", 1001L);

        assertThat(response.idempotent()).isFalse();
        assertThat(response.status()).isEqualTo("SETTLED");
        verify(paymentMapper).updateWalletBalance(1001L, 300L, 50L);
        verify(paymentMapper).updateWalletBalance(2001L, 200L, 0L);
    }

    @Test
    void shouldCreateMissingSellerWalletWithoutGapLockDuringSettle() {
        when(paymentMapper.findTradeByOrderNoForUpdate("O2010"))
                .thenReturn(trade("O2010", "P-2010", 1001L, 2001L, 150L, TradePaymentStatus.RESERVED.code()));
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 300L, 200L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 300L, 200L));
        when(paymentMapper.findWalletByUserId(2001L)).thenReturn(null);
        when(paymentMapper.findWalletByUserIdForUpdate(2001L)).thenReturn(wallet(2001L, 0L, 0L));
        when(paymentMapper.markTradeSettled(eq("O2010"), eq(TradePaymentStatus.RESERVED.code()),
                eq(TradePaymentStatus.SETTLED.code()), any())).thenReturn(1);

        SettleOrderPaymentResponse response = paymentService.settleOrderPayment("O2010", "BUYER", 1001L);

        assertThat(response.idempotent()).isFalse();
        verify(paymentMapper).insertWalletAccount(2001L, 0L, 0L);
        InOrder inOrder = inOrder(paymentMapper);
        inOrder.verify(paymentMapper).findWalletByUserId(2001L);
        inOrder.verify(paymentMapper).insertWalletAccount(2001L, 0L, 0L);
        inOrder.verify(paymentMapper).findWalletByUserIdForUpdate(2001L);
    }

    @Test
    void shouldReturnIdempotentWhenAlreadySettled() {
        when(paymentMapper.findTradeByOrderNoForUpdate("O2002"))
                .thenReturn(trade("O2002", "P-2002", 1001L, 2001L, 150L, TradePaymentStatus.SETTLED.code()));

        SettleOrderPaymentResponse response = paymentService.settleOrderPayment("O2002", "ADMIN", 9001L);

        assertThat(response.idempotent()).isTrue();
        verify(paymentMapper, never()).updateWalletBalance(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRejectSettleWhenTradeStatusInvalid() {
        when(paymentMapper.findTradeByOrderNoForUpdate("O2003"))
                .thenReturn(trade("O2003", "P-2003", 1001L, 2001L, 150L, TradePaymentStatus.RELEASED.code()));

        assertThatThrownBy(() -> paymentService.settleOrderPayment("O2003", "BUYER", 1001L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 60003);
    }

    @Test
    void shouldReleaseOrderPaymentSuccessfully() {
        when(paymentMapper.findTradeByOrderNoForUpdate("O3001"))
                .thenReturn(trade("O3001", "P-3001", 1001L, 2001L, 120L, TradePaymentStatus.RESERVED.code()));
        when(paymentMapper.findWalletByUserId(1001L)).thenReturn(wallet(1001L, 300L, 200L));
        when(paymentMapper.findWalletByUserIdForUpdate(1001L)).thenReturn(wallet(1001L, 300L, 200L));
        when(paymentMapper.markTradeReleased(eq("O3001"), eq(TradePaymentStatus.RESERVED.code()),
                eq(TradePaymentStatus.RELEASED.code()), any())).thenReturn(1);

        ReleaseOrderPaymentResponse response = paymentService.releaseOrderPayment("O3001", "manual");

        assertThat(response.idempotent()).isFalse();
        assertThat(response.status()).isEqualTo("RELEASED");
        verify(paymentMapper).updateWalletBalance(1001L, 420L, 80L);
    }

    @Test
    void shouldReturnIdempotentWhenAlreadyReleased() {
        when(paymentMapper.findTradeByOrderNoForUpdate("O3002"))
                .thenReturn(trade("O3002", "P-3002", 1001L, 2001L, 120L, TradePaymentStatus.RELEASED.code()));

        ReleaseOrderPaymentResponse response = paymentService.releaseOrderPayment("O3002", "manual");

        assertThat(response.idempotent()).isTrue();
        assertThat(response.status()).isEqualTo("RELEASED");
        verify(paymentMapper, never()).updateWalletBalance(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRejectReleaseWhenTradeStatusInvalid() {
        when(paymentMapper.findTradeByOrderNoForUpdate("O3003"))
                .thenReturn(trade("O3003", "P-3003", 1001L, 2001L, 120L, TradePaymentStatus.SETTLED.code()));

        assertThatThrownBy(() -> paymentService.releaseOrderPayment("O3003", "manual"))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 60003);
    }

    private WalletAccountRecord wallet(Long userId, Long available, Long frozen) {
        return new WalletAccountRecord(1L, userId, available, frozen, 0);
    }

    private TradePaymentRecord trade(String orderNo, String paymentNo, Long buyerUserId,
                                     Long sellerUserId, Long amountCent, Integer status) {
        return new TradePaymentRecord(1L, orderNo, paymentNo, buyerUserId, sellerUserId, amountCent, status);
    }
}
