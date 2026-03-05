package moe.hhm.shiori.payment.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "payment.internal-api")
public class InternalApiProperties {

    private static final String DEFAULT_TOKEN = "dev-order-payment-internal-token-please-change-0001";

    private String token = DEFAULT_TOKEN;
    private boolean requireToken = true;

    @PostConstruct
    void validate() {
        if (!requireToken) {
            return;
        }
        if (!StringUtils.hasText(token) || token.trim().length() < 32) {
            throw new IllegalStateException("payment.internal-api.token 未配置或长度不足(至少32位)");
        }
        if (DEFAULT_TOKEN.equals(token.trim())) {
            throw new IllegalStateException("payment.internal-api.token 不能使用默认值");
        }
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isRequireToken() {
        return requireToken;
    }

    public void setRequireToken(boolean requireToken) {
        this.requireToken = requireToken;
    }
}
