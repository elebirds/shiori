package moe.hhm.shiori.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "product.mq")
public class ProductMqProperties {

    private boolean enabled = true;
    private String eventExchange = "shiori.product.event";
    private String productPublishedRoutingKey = "product.published";

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

    public String getProductPublishedRoutingKey() {
        return productPublishedRoutingKey;
    }

    public void setProductPublishedRoutingKey(String productPublishedRoutingKey) {
        this.productPublishedRoutingKey = productPublishedRoutingKey;
    }
}
