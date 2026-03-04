package moe.hhm.shiori.product.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductMetrics {

    private static final String QUERY_TOTAL = "shiori_product_query_total";

    private final MeterRegistry meterRegistry;

    public ProductMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incQuery(String filterCombo) {
        meterRegistry.counter(QUERY_TOTAL, "filter_combo", sanitize(filterCombo)).increment();
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "none";
        }
        return value.trim();
    }
}
