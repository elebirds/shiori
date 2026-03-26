package moe.hhm.shiori.product.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductMetrics {

    private static final String QUERY_TOTAL = "shiori_product_query_total";
    private static final String STOCK_DEDUCT_LATENCY = "shiori_product_stock_deduct_latency_seconds";

    private final MeterRegistry meterRegistry;

    public ProductMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incQuery(String filterCombo) {
        meterRegistry.counter(QUERY_TOTAL, "filter_combo", sanitize(filterCombo)).increment();
    }

    public void recordStockDeductLatency(String result, Duration duration) {
        Timer.builder(STOCK_DEDUCT_LATENCY)
                .tag("result", sanitize(result))
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(duration);
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "none";
        }
        return value.trim();
    }
}
