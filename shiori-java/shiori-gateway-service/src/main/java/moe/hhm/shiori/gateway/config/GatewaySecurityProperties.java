package moe.hhm.shiori.gateway.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public class GatewaySecurityProperties {

    private final Jwt jwt = new Jwt();
    private final Auth auth = new Auth();
    private final GatewaySign gatewaySign = new GatewaySign();
    private final RateLimit rateLimit = new RateLimit();

    public Jwt getJwt() {
        return jwt;
    }

    public Auth getAuth() {
        return auth;
    }

    public GatewaySign getGatewaySign() {
        return gatewaySign;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class Jwt {
        private String hmacSecret;
        private String issuer;
        private long accessTtlSeconds = 900;
        private long refreshTtlSeconds = 604800;

        public String getHmacSecret() {
            return hmacSecret;
        }

        public void setHmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public long getAccessTtlSeconds() {
            return accessTtlSeconds;
        }

        public void setAccessTtlSeconds(long accessTtlSeconds) {
            this.accessTtlSeconds = accessTtlSeconds;
        }

        public long getRefreshTtlSeconds() {
            return refreshTtlSeconds;
        }

        public void setRefreshTtlSeconds(long refreshTtlSeconds) {
            this.refreshTtlSeconds = refreshTtlSeconds;
        }
    }

    public static class Auth {
        private List<String> whitelist = new ArrayList<>();
        private List<String> anonymousGetPaths = new ArrayList<>(List.of(
                "/api/product/**",
                "/api/v2/product/**"
        ));

        public List<String> getWhitelist() {
            return whitelist;
        }

        public void setWhitelist(List<String> whitelist) {
            this.whitelist = whitelist;
        }

        public List<String> getAnonymousGetPaths() {
            return anonymousGetPaths;
        }

        public void setAnonymousGetPaths(List<String> anonymousGetPaths) {
            this.anonymousGetPaths = anonymousGetPaths;
        }
    }

    public static class GatewaySign {
        private String internalSecret;
        private long maxSkewSeconds = 300;

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
    }

    public static class RateLimit {
        private boolean enabled = true;
        private int loginPerSecond = 8;
        private int orderCreatePerSecond = 10;
        private int orderPayPerSecond = 15;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLoginPerSecond() {
            return loginPerSecond;
        }

        public void setLoginPerSecond(int loginPerSecond) {
            this.loginPerSecond = loginPerSecond;
        }

        public int getOrderCreatePerSecond() {
            return orderCreatePerSecond;
        }

        public void setOrderCreatePerSecond(int orderCreatePerSecond) {
            this.orderCreatePerSecond = orderCreatePerSecond;
        }

        public int getOrderPayPerSecond() {
            return orderPayPerSecond;
        }

        public void setOrderPayPerSecond(int orderPayPerSecond) {
            this.orderPayPerSecond = orderPayPerSecond;
        }
    }
}
