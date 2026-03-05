package moe.hhm.shiori.order.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "order.payment-client")
public class PaymentClientProperties {

    private String serviceBaseUrl = "http://shiori-payment-service";
    private String internalToken = "dev-order-payment-internal-token-please-change-0001";

    @PostConstruct
    void validate() {
        if (!StringUtils.hasText(internalToken) || internalToken.trim().length() < 32) {
            throw new IllegalStateException("order.payment-client.internal-token 未配置或长度不足(至少32位)");
        }
    }

    public String getServiceBaseUrl() {
        return serviceBaseUrl;
    }

    public void setServiceBaseUrl(String serviceBaseUrl) {
        this.serviceBaseUrl = serviceBaseUrl;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }
}
