package moe.hhm.shiori.common.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.gateway-sign")
public class GatewaySignProperties {

    private boolean enabled = true;
    private String internalSecret = "change-me-internal-sign-secret-change-me";
    private long maxSkewSeconds = 300;
    private List<String> permitAllPaths = new ArrayList<>(List.of(
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/user/auth/register",
            "/api/user/auth/login",
            "/api/user/auth/refresh",
            "/api/user/auth/logout"
    ));
    private List<String> anonymousGetPaths = new ArrayList<>(List.of(
            "/api/product/**"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        this.internalSecret = internalSecret;
    }

    public long getMaxSkewSeconds() {
        return maxSkewSeconds;
    }

    public void setMaxSkewSeconds(long maxSkewSeconds) {
        this.maxSkewSeconds = maxSkewSeconds;
    }

    public List<String> getPermitAllPaths() {
        return permitAllPaths;
    }

    public void setPermitAllPaths(List<String> permitAllPaths) {
        this.permitAllPaths = permitAllPaths;
    }

    public List<String> getAnonymousGetPaths() {
        return anonymousGetPaths;
    }

    public void setAnonymousGetPaths(List<String> anonymousGetPaths) {
        this.anonymousGetPaths = anonymousGetPaths;
    }
}
