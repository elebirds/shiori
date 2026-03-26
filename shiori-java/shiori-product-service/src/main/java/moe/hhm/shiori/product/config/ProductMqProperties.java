package moe.hhm.shiori.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "product.mq")
public class ProductMqProperties {

    private String eventExchange = "shiori.product.event";
    private String productPublishedRoutingKey = "product.published";

    public String getEventExchange() {
        return eventExchange;
    }

    public void setEventExchange(String eventExchange) {
        this.eventExchange = eventExchange;
    }

    public String getProductPublishedRoutingKey() {
        return productPublishedRoutingKey;
    }

    public void setProductPublishedRoutingKey(String productPublishedRoutingKey) {
        this.productPublishedRoutingKey = productPublishedRoutingKey;
    }
}
