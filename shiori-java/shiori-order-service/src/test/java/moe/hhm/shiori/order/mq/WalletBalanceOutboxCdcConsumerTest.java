package moe.hhm.shiori.order.mq;

import moe.hhm.shiori.order.service.OrderRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WalletBalanceOutboxCdcConsumerTest {

    @Mock
    private OrderRefundService orderRefundService;

    private WalletBalanceOutboxCdcConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new WalletBalanceOutboxCdcConsumer(new ObjectMapper(), orderRefundService);
    }

    @Test
    void shouldRetryPendingRefundsWhenWalletBalanceOutboxCdcMessageValid() {
        String message = """
                {
                  "event_id":"event-1",
                  "aggregate_type":"wallet",
                  "aggregate_id":"2001",
                  "message_key":"2001",
                  "user_id":2001,
                  "biz_no":"O1001",
                  "type":"WalletBalanceChanged",
                  "payload":"{\\"eventId\\":\\"event-1\\",\\"type\\":\\"WalletBalanceChanged\\",\\"aggregateId\\":\\"2001\\",\\"createdAt\\":\\"2026-03-07T00:00:00Z\\",\\"payload\\":{\\"userId\\":2001,\\"availableBalanceCent\\":500,\\"frozenBalanceCent\\":0,\\"bizNo\\":\\"O1001\\",\\"occurredAt\\":\\"2026-03-07T00:00:00Z\\"}}",
                  "status":"PENDING"
                }
                """;

        consumer.onMessage(message);

        verify(orderRefundService).retryPendingRefundsBySeller(2001L);
    }

    @Test
    void shouldIgnoreWhenAggregateTypeNotWallet() {
        String message = """
                {
                  "event_id":"event-2",
                  "aggregate_type":"product",
                  "aggregate_id":"2001",
                  "message_key":"2001",
                  "user_id":2001,
                  "biz_no":"O1001",
                  "type":"WalletBalanceChanged",
                  "payload":"{}",
                  "status":"PENDING"
                }
                """;

        consumer.onMessage(message);

        verifyNoInteractions(orderRefundService);
    }

    @Test
    void shouldIgnoreWhenOutboxStatusNotPending() {
        String message = """
                {
                  "event_id":"event-3",
                  "aggregate_type":"wallet",
                  "aggregate_id":"2001",
                  "message_key":"2001",
                  "user_id":2001,
                  "biz_no":"O1001",
                  "type":"WalletBalanceChanged",
                  "payload":"{\\"eventId\\":\\"event-3\\",\\"type\\":\\"WalletBalanceChanged\\",\\"aggregateId\\":\\"2001\\",\\"createdAt\\":\\"2026-03-07T00:00:00Z\\",\\"payload\\":{\\"userId\\":2001,\\"availableBalanceCent\\":500,\\"frozenBalanceCent\\":0,\\"bizNo\\":\\"O1001\\",\\"occurredAt\\":\\"2026-03-07T00:00:00Z\\"}}",
                  "status":"SENT"
                }
                """;

        consumer.onMessage(message);

        verifyNoInteractions(orderRefundService);
    }
}
