package moe.hhm.shiori.order.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OrderMetrics {

    private static final String OUTBOX_RELAY_TOTAL = "shiori_order_outbox_relay_total";
    private static final String TIMEOUT_CONSUME_TOTAL = "shiori_order_timeout_consume_total";
    private static final String STATE_TRANSITION_TOTAL = "shiori_order_state_transition_total";
    private static final String TRANSITION_TOTAL = "shiori_order_transition_total";
    private static final String IDEMPOTENCY_TOTAL = "shiori_order_idempotency_total";
    private static final String CHAT_TO_ORDER_CLICK_TOTAL = "chat_to_order_click_total";
    private static final String CHAT_TO_ORDER_SUBMIT_TOTAL = "chat_to_order_submit_total";
    private static final String CHAT_TRADE_STATUS_CARD_SENT_TOTAL = "chat_trade_status_card_sent_total";

    private final MeterRegistry meterRegistry;

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incOutboxRelay(String result, String type) {
        meterRegistry.counter(
                OUTBOX_RELAY_TOTAL,
                "result", sanitize(result),
                "type", sanitize(type)
        ).increment();
    }

    public void incTimeoutConsume(String result) {
        meterRegistry.counter(
                TIMEOUT_CONSUME_TOTAL,
                "result", sanitize(result)
        ).increment();
    }

    public void incStateTransition(String from, String to) {
        meterRegistry.counter(
                STATE_TRANSITION_TOTAL,
                "from", sanitize(from),
                "to", sanitize(to)
        ).increment();
        meterRegistry.counter(
                TRANSITION_TOTAL,
                "from", sanitize(from),
                "to", sanitize(to),
                "source", "unknown"
        ).increment();
    }

    public void incStateTransition(String from, String to, String source) {
        meterRegistry.counter(
                STATE_TRANSITION_TOTAL,
                "from", sanitize(from),
                "to", sanitize(to)
        ).increment();
        meterRegistry.counter(
                TRANSITION_TOTAL,
                "from", sanitize(from),
                "to", sanitize(to),
                "source", sanitize(source)
        ).increment();
    }

    public void incIdempotency(String operation, String result) {
        meterRegistry.counter(
                IDEMPOTENCY_TOTAL,
                "operation", sanitize(operation),
                "result", sanitize(result)
        ).increment();
    }

    public void incChatToOrderSubmit(String source) {
        meterRegistry.counter(
                CHAT_TO_ORDER_SUBMIT_TOTAL,
                "source", sanitize(source)
        ).increment();
    }

    public void incChatToOrderClick(String source) {
        meterRegistry.counter(
                CHAT_TO_ORDER_CLICK_TOTAL,
                "source", sanitize(source)
        ).increment();
    }

    public void incChatTradeStatusCardSent(String status) {
        meterRegistry.counter(
                CHAT_TRADE_STATUS_CARD_SENT_TOTAL,
                "status", sanitize(status)
        ).increment();
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim();
    }
}
