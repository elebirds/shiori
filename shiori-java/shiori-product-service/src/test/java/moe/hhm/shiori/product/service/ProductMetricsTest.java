package moe.hhm.shiori.product.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsTest {

    @Test
    void shouldRecordStockDeductLatencyMetric() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ProductMetrics productMetrics = new ProductMetrics(meterRegistry);

        productMetrics.recordStockDeductLatency("success", Duration.ofMillis(25));

        Timer timer = meterRegistry.find("shiori_product_stock_deduct_latency_seconds")
                .tag("result", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(25.0d);
    }
}
