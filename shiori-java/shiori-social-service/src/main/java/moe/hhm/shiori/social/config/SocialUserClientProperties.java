package moe.hhm.shiori.social.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "social.user-client")
public class SocialUserClientProperties {

    private String serviceBaseUrl = "http://shiori-user-service";

    public String getServiceBaseUrl() {
        return serviceBaseUrl;
    }

    public void setServiceBaseUrl(String serviceBaseUrl) {
        this.serviceBaseUrl = serviceBaseUrl;
    }
}
