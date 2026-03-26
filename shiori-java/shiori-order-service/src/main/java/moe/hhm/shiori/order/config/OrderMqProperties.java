package moe.hhm.shiori.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "order.mq")
public class OrderMqProperties {

    private String eventExchange = "shiori.order.event";
    private String orderCreatedRoutingKey = "order.created";
    private String orderPaidRoutingKey = "order.paid";
    private String orderCanceledRoutingKey = "order.canceled";
    private String orderDeliveredRoutingKey = "order.delivered";
    private String orderFinishedRoutingKey = "order.finished";

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

}
