package moe.hhm.shiori.order.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OrderMetrics {

    private static final String OUTBOX_RELAY_TOTAL = "shiori_order_outbox_relay_total";
    private static final String STATE_TRANSITION_TOTAL = "shiori_order_state_transition_total";
    private static final String TRANSITION_TOTAL = "shiori_order_transition_total";
    private static final String IDEMPOTENCY_TOTAL = "shiori_order_idempotency_total";
    private static final String CHAT_TO_ORDER_CLICK_TOTAL = "chat_to_order_click_total";
    private static final String CHAT_TO_ORDER_SUBMIT_TOTAL = "chat_to_order_submit_total";
    private static final String CHAT_TRADE_STATUS_CARD_SENT_TOTAL = "chat_trade_status_card_sent_total";
    private static final String ORDER_REVIEW_SUBMIT_TOTAL = "shiori_order_review_submit_total";
    private static final String ORDER_REVIEW_UPDATE_TOTAL = "shiori_order_review_update_total";
    private static final String ORDER_REVIEW_MODERATION_TOTAL = "shiori_order_review_moderation_total";
    private static final String ORDER_CREDIT_QUERY_TOTAL = "shiori_order_credit_query_total";
    private static final String ORDER_COMMAND_TOTAL = "shiori_order_command_total";
    private static final String ORDER_COMMAND_RECOVERY_TOTAL = "shiori_order_command_recovery_total";
    private static final String ORDER_COMMAND_COMPENSATION_TOTAL = "shiori_order_command_compensation_total";
    private static final String ORDER_COMMAND_PROCESSING_TOTAL = "shiori_order_command_processing_total";

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

    public void incOrderReviewSubmit(String role, String result) {
        meterRegistry.counter(
                ORDER_REVIEW_SUBMIT_TOTAL,
                "role", sanitize(role),
                "result", sanitize(result)
        ).increment();
    }

    public void incOrderReviewUpdate(String role, String result) {
        meterRegistry.counter(
                ORDER_REVIEW_UPDATE_TOTAL,
                "role", sanitize(role),
                "result", sanitize(result)
        ).increment();
    }

    public void incOrderReviewModeration(String action) {
        meterRegistry.counter(
                ORDER_REVIEW_MODERATION_TOTAL,
                "action", sanitize(action)
        ).increment();
    }

    public void incOrderCreditQuery(String endpoint) {
        meterRegistry.counter(
                ORDER_CREDIT_QUERY_TOTAL,
                "endpoint", sanitize(endpoint)
        ).increment();
    }

    public void incOrderCommand(String type, String state) {
        meterRegistry.counter(
                ORDER_COMMAND_TOTAL,
                "type", sanitize(type),
                "state", sanitize(state)
        ).increment();
    }

    public void incOrderCommandRecovery(String type, String result) {
        meterRegistry.counter(
                ORDER_COMMAND_RECOVERY_TOTAL,
                "type", sanitize(type),
                "result", sanitize(result)
        ).increment();
    }

    public void incOrderCommandCompensation(String type, String result) {
        meterRegistry.counter(
                ORDER_COMMAND_COMPENSATION_TOTAL,
                "type", sanitize(type),
                "result", sanitize(result)
        ).increment();
    }

    public void incOrderCommandProcessing(String type) {
        meterRegistry.counter(
                ORDER_COMMAND_PROCESSING_TOTAL,
                "type", sanitize(type)
        ).increment();
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim();
    }
}
