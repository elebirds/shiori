package moe.hhm.shiori.order.mq;

import moe.hhm.shiori.order.event.WalletBalanceChangedPayload;
import moe.hhm.shiori.order.service.OrderRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletBalanceOutboxCdcConsumeServiceTest {

    @Mock
    private KafkaConsumeLogService consumeLogService;
    @Mock
    private OrderRefundService orderRefundService;

    private WalletBalanceOutboxCdcConsumeService service;

    @BeforeEach
    void setUp() {
        service = new WalletBalanceOutboxCdcConsumeService(consumeLogService, orderRefundService);
    }

    @Test
    void shouldSkipDuplicateSucceededEvent() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("shiori.cdc.payment.outbox.raw", 0, 10L, "group-a");
        WalletBalanceChangedPayload payload =
                new WalletBalanceChangedPayload(2001L, 500L, 0L, "O1001", "2026-03-07T00:00:00Z");
        when(consumeLogService.startProcessing("event-1", "WalletBalanceChanged", metadata)).thenReturn(false);

        service.handle("event-1", payload, metadata);

        verify(orderRefundService, never()).retryPendingRefundsBySellerOrThrow(any());
        verify(consumeLogService, never()).markSucceeded(any(), any());
        verify(consumeLogService, never()).markFailed(any(), any(), any());
    }

    @Test
    void shouldMarkSucceededAfterRefundRetryBatchCompleted() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("shiori.cdc.payment.outbox.raw", 1, 11L, "group-a");
        WalletBalanceChangedPayload payload =
                new WalletBalanceChangedPayload(2001L, 500L, 0L, "O1001", "2026-03-07T00:00:00Z");
        when(consumeLogService.startProcessing("event-2", "WalletBalanceChanged", metadata)).thenReturn(true);

        service.handle("event-2", payload, metadata);

        verify(orderRefundService).retryPendingRefundsBySellerOrThrow(2001L);
        verify(consumeLogService).markSucceeded("event-2", metadata);
    }

    @Test
    void shouldMarkFailedAndRethrowWhenRefundRetryBatchFails() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("shiori.cdc.payment.outbox.raw", 2, 12L, "group-a");
        WalletBalanceChangedPayload payload =
                new WalletBalanceChangedPayload(2001L, 500L, 0L, "O1001", "2026-03-07T00:00:00Z");
        when(consumeLogService.startProcessing("event-3", "WalletBalanceChanged", metadata)).thenReturn(true);
        doThrow(new OrderRefundBatchRetryException(2001L, 1))
                .when(orderRefundService)
                .retryPendingRefundsBySellerOrThrow(2001L);

        assertThatThrownBy(() -> service.handle("event-3", payload, metadata))
                .isInstanceOf(OrderRefundBatchRetryException.class);

        verify(consumeLogService).markFailed(eq("event-3"), eq(metadata), any(OrderRefundBatchRetryException.class));
    }
}
