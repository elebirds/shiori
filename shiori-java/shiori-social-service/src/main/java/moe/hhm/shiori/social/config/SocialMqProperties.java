package moe.hhm.shiori.social.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "social.mq")
public class SocialMqProperties {

    private boolean enabled = true;
    private String productEventExchange = "shiori.product.event";
    private String productPublishedRoutingKey = "product.published";
    private String productPublishedQueue = "q.social.product.published";
    private String productPublishedDlxExchange = "shiori.social.product.dlx";
    private String productPublishedDlqRoutingKey = "product.published.dlq";
    private String productPublishedDlqQueue = "q.social.product.published.dlq";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProductEventExchange() {
        return productEventExchange;
    }

    public void setProductEventExchange(String productEventExchange) {
        this.productEventExchange = productEventExchange;
    }

    public String getProductPublishedRoutingKey() {
        return productPublishedRoutingKey;
    }

    public void setProductPublishedRoutingKey(String productPublishedRoutingKey) {
        this.productPublishedRoutingKey = productPublishedRoutingKey;
    }

    public String getProductPublishedQueue() {
        return productPublishedQueue;
    }

    public void setProductPublishedQueue(String productPublishedQueue) {
        this.productPublishedQueue = productPublishedQueue;
    }

    public String getProductPublishedDlxExchange() {
        return productPublishedDlxExchange;
    }

    public void setProductPublishedDlxExchange(String productPublishedDlxExchange) {
        this.productPublishedDlxExchange = productPublishedDlxExchange;
    }

    public String getProductPublishedDlqRoutingKey() {
        return productPublishedDlqRoutingKey;
    }

    public void setProductPublishedDlqRoutingKey(String productPublishedDlqRoutingKey) {
        this.productPublishedDlqRoutingKey = productPublishedDlqRoutingKey;
    }

    public String getProductPublishedDlqQueue() {
        return productPublishedDlqQueue;
    }

    public void setProductPublishedDlqQueue(String productPublishedDlqQueue) {
        this.productPublishedDlqQueue = productPublishedDlqQueue;
    }
}
