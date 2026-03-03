package moe.hhm.shiori.order.service;

import java.util.List;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.model.OutboxEventRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxRelayService outboxRelayService;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties();
        outboxRelayService = new OutboxRelayService(
                orderMapper,
                rabbitTemplate,
                orderProperties,
                new OrderMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void shouldMarkSentWhenPublishSuccess() {
        OutboxEventRecord event = new OutboxEventRecord(
                1L, "event-1", "order-1", "OrderCreated",
                "{\"eventId\":\"event-1\"}", "shiori.order.event", "order.created",
                "PENDING", 0, null, null, null, null
        );
        when(orderMapper.listRelayCandidates(100)).thenReturn(List.of(event));

        outboxRelayService.relayPendingEvents();

        verify(rabbitTemplate).convertAndSend("shiori.order.event", "order.created", "{\"eventId\":\"event-1\"}");
        verify(orderMapper).markOutboxSent(1L);
    }

    @Test
    void shouldMarkFailedWhenPublishThrows() {
        OutboxEventRecord event = new OutboxEventRecord(
                2L, "event-2", "order-2", "OrderPaid",
                "{\"eventId\":\"event-2\"}", "shiori.order.event", "order.paid",
                "FAILED", 1, "last", null, null, null
        );
        when(orderMapper.listRelayCandidates(100)).thenReturn(List.of(event));
        doThrow(new RuntimeException("publish failed")).when(rabbitTemplate)
                .convertAndSend("shiori.order.event", "order.paid", "{\"eventId\":\"event-2\"}");

        outboxRelayService.relayPendingEvents();

        verify(orderMapper).markOutboxFailed(eq(2L), eq(2), eq("publish failed"), any());
    }
}
