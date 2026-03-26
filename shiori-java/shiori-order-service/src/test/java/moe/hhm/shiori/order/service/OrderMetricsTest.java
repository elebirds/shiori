package moe.hhm.shiori.order.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMetricsTest {

    @Test
    void shouldExposeOrderCreateTransitionMetricForQps() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OrderMetrics orderMetrics = new OrderMetrics(meterRegistry);

        orderMetrics.incStateTransition("NEW", "UNPAID", "buyer");

        Counter counter = meterRegistry.find("shiori_order_transition_total")
                .tags("from", "NEW", "to", "UNPAID", "source", "buyer")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0d);
    }

    @Test
    void shouldExposeKafkaConsumerLagGauge() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OrderMetrics orderMetrics = new OrderMetrics(meterRegistry);

        orderMetrics.recordKafkaConsumerLagSeconds("wallet_balance_outbox", 3.5d);

        Gauge gauge = meterRegistry.find("shiori_order_kafka_consumer_lag_seconds")
                .tag("consumer", "wallet_balance_outbox")
                .gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(3.5d);
    }
}
