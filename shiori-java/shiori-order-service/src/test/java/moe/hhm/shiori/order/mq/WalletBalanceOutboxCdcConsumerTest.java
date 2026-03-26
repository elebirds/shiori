package moe.hhm.shiori.order.mq;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import moe.hhm.shiori.order.service.OrderMetrics;
import moe.hhm.shiori.order.service.OrderRefundService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WalletBalanceOutboxCdcConsumerTest {

    private SimpleMeterRegistry meterRegistry;

    @Mock
    private OrderRefundService orderRefundService;

    private WalletBalanceOutboxCdcConsumer consumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new WalletBalanceOutboxCdcConsumer(
                new ObjectMapper(),
                new OrderMetrics(meterRegistry),
                orderRefundService
        );
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

        consumer.onMessage(new ConsumerRecord<>(
                "shiori.cdc.payment.outbox.raw",
                0,
                10L,
                System.currentTimeMillis() - 2_000L,
                TimestampType.CREATE_TIME,
                0,
                message.length(),
                null,
                message,
                new RecordHeaders(),
                Optional.empty()
        ));

        verify(orderRefundService).retryPendingRefundsBySeller(2001L);
        Gauge gauge = meterRegistry.find("shiori_order_kafka_consumer_lag_seconds")
                .tag("consumer", "wallet_balance_outbox")
                .gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isBetween(1.0d, 10.0d);
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

        consumer.onMessage(new ConsumerRecord<String, String>("shiori.cdc.payment.outbox.raw", 0, 11L, null, message));

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

        consumer.onMessage(new ConsumerRecord<String, String>("shiori.cdc.payment.outbox.raw", 0, 12L, null, message));

        verifyNoInteractions(orderRefundService);
    }
}
