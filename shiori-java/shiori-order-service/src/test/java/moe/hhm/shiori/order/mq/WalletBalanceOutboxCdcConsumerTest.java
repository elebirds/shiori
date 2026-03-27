package moe.hhm.shiori.order.mq;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import moe.hhm.shiori.order.event.WalletBalanceChangedPayload;
import moe.hhm.shiori.order.service.OrderMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class WalletBalanceOutboxCdcConsumerTest {

    @Mock
    private WalletBalanceOutboxCdcConsumeService consumeService;
    @Mock
    private Acknowledgment acknowledgment;

    private SimpleMeterRegistry meterRegistry;
    private WalletBalanceOutboxCdcConsumer consumer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new WalletBalanceOutboxCdcConsumer(
                new ObjectMapper(),
                consumeService,
                new OrderMetrics(meterRegistry),
                "shiori-order-wallet-cdc"
        );
    }

    @Test
    void shouldConsumeAndAcknowledgeWhenWalletBalanceOutboxCdcMessageValid() {
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
        ), acknowledgment);

        verify(consumeService).handle(
                eq("event-1"),
                eq(new WalletBalanceChangedPayload(2001L, 500L, 0L, "O1001", "2026-03-07T00:00:00Z")),
                argThat(metadata -> metadata != null
                        && "shiori.cdc.payment.outbox.raw".equals(metadata.topic())
                        && metadata.partition() == 0
                        && metadata.offset() == 10L
                        && "shiori-order-wallet-cdc".equals(metadata.consumerGroup()))
        );
        verify(acknowledgment).acknowledge();
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

        consumer.onMessage(
                new ConsumerRecord<String, String>("shiori.cdc.payment.outbox.raw", 0, 11L, null, message),
                acknowledgment
        );

        verifyNoInteractions(consumeService);
        verify(acknowledgment).acknowledge();
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

        consumer.onMessage(
                new ConsumerRecord<String, String>("shiori.cdc.payment.outbox.raw", 0, 12L, null, message),
                acknowledgment
        );

        verifyNoInteractions(consumeService);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldThrowWithoutAcknowledgingWhenEnvelopeInvalid() {
        String message = """
                {
                  "event_id":"event-4",
                  "aggregate_type":"wallet",
                  "aggregate_id":"2001",
                  "message_key":"2001",
                  "user_id":2001,
                  "biz_no":"O1001",
                  "type":"WalletBalanceChanged",
                  "payload":"not-json",
                  "status":"PENDING"
                }
                """;

        assertThatThrownBy(() -> consumer.onMessage(
                new ConsumerRecord<String, String>("shiori.cdc.payment.outbox.raw", 0, 13L, null, message),
                acknowledgment))
                .isInstanceOf(NonRetryableKafkaConsumerException.class);

        verifyNoInteractions(consumeService);
        verifyNoMoreInteractions(acknowledgment);
    }
}
