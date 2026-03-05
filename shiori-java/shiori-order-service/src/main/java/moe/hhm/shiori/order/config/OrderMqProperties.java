package moe.hhm.shiori.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "order.mq")
public class OrderMqProperties {

    private boolean enabled = true;
    private String eventExchange = "shiori.order.event";
    private String orderCreatedRoutingKey = "order.created";
    private String orderPaidRoutingKey = "order.paid";
    private String orderCanceledRoutingKey = "order.canceled";
    private String orderDeliveredRoutingKey = "order.delivered";
    private String orderFinishedRoutingKey = "order.finished";
    private String delayExchange = "shiori.order.delay";
    private String delayRoutingKey = "order.timeout";
    private String timeoutDlxExchange = "shiori.order.timeout.dlx";
    private String timeoutDelayQueue = "q.order.timeout.delay";
    private String timeoutConsumeQueue = "q.order.timeout.consume";
    private long timeoutTtlMs = 900000;
    private String paymentEventExchange = "shiori.payment.event";
    private String walletBalanceChangedRoutingKey = "wallet.balance.changed";
    private String walletBalanceChangedQueue = "q.order.wallet.balance.changed";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEventExchange() {
        return eventExchange;
    }

    public void setEventExchange(String eventExchange) {
        this.eventExchange = eventExchange;
    }

    public String getOrderCreatedRoutingKey() {
        return orderCreatedRoutingKey;
    }

    public void setOrderCreatedRoutingKey(String orderCreatedRoutingKey) {
        this.orderCreatedRoutingKey = orderCreatedRoutingKey;
    }

    public String getOrderPaidRoutingKey() {
        return orderPaidRoutingKey;
    }

    public void setOrderPaidRoutingKey(String orderPaidRoutingKey) {
        this.orderPaidRoutingKey = orderPaidRoutingKey;
    }

    public String getOrderCanceledRoutingKey() {
        return orderCanceledRoutingKey;
    }

    public void setOrderCanceledRoutingKey(String orderCanceledRoutingKey) {
        this.orderCanceledRoutingKey = orderCanceledRoutingKey;
    }

    public String getOrderDeliveredRoutingKey() {
        return orderDeliveredRoutingKey;
    }

    public void setOrderDeliveredRoutingKey(String orderDeliveredRoutingKey) {
        this.orderDeliveredRoutingKey = orderDeliveredRoutingKey;
    }

    public String getOrderFinishedRoutingKey() {
        return orderFinishedRoutingKey;
    }

    public void setOrderFinishedRoutingKey(String orderFinishedRoutingKey) {
        this.orderFinishedRoutingKey = orderFinishedRoutingKey;
    }

    public String getDelayExchange() {
        return delayExchange;
    }

    public void setDelayExchange(String delayExchange) {
        this.delayExchange = delayExchange;
    }

    public String getDelayRoutingKey() {
        return delayRoutingKey;
    }

    public void setDelayRoutingKey(String delayRoutingKey) {
        this.delayRoutingKey = delayRoutingKey;
    }

    public String getTimeoutDlxExchange() {
        return timeoutDlxExchange;
    }

    public void setTimeoutDlxExchange(String timeoutDlxExchange) {
        this.timeoutDlxExchange = timeoutDlxExchange;
    }

    public String getTimeoutDelayQueue() {
        return timeoutDelayQueue;
    }

    public void setTimeoutDelayQueue(String timeoutDelayQueue) {
        this.timeoutDelayQueue = timeoutDelayQueue;
    }

    public String getTimeoutConsumeQueue() {
        return timeoutConsumeQueue;
    }

    public void setTimeoutConsumeQueue(String timeoutConsumeQueue) {
        this.timeoutConsumeQueue = timeoutConsumeQueue;
    }

    public long getTimeoutTtlMs() {
        return timeoutTtlMs;
    }

    public void setTimeoutTtlMs(long timeoutTtlMs) {
        this.timeoutTtlMs = timeoutTtlMs;
    }

    public String getPaymentEventExchange() {
        return paymentEventExchange;
    }

    public void setPaymentEventExchange(String paymentEventExchange) {
        this.paymentEventExchange = paymentEventExchange;
    }

    public String getWalletBalanceChangedRoutingKey() {
        return walletBalanceChangedRoutingKey;
    }

    public void setWalletBalanceChangedRoutingKey(String walletBalanceChangedRoutingKey) {
        this.walletBalanceChangedRoutingKey = walletBalanceChangedRoutingKey;
    }

    public String getWalletBalanceChangedQueue() {
        return walletBalanceChangedQueue;
    }

    public void setWalletBalanceChangedQueue(String walletBalanceChangedQueue) {
        this.walletBalanceChangedQueue = walletBalanceChangedQueue;
    }
}
