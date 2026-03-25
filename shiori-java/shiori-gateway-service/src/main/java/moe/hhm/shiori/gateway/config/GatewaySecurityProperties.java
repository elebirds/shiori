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
    private final Authz authz = new Authz();

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

    public Authz getAuthz() {
        return authz;
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
                "/api/v2/product/**",
                "/api/user/profiles/**",
                "/api/user/media/avatar/**",
                "/api/v2/product/users/**"
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
        private int loginPerSecond = 20;
        private int orderCreatePerSecond = 30;
        private int orderPayPerSecond = 50;

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

    public static class Authz {
        private boolean enabled = true;
        private String userServiceBaseUrl = "http://shiori-user-service";
        private int queryTimeoutMs = 800;
        private final Cache cache = new Cache();
        private final Degrade degrade = new Degrade();
        private final Event event = new Event();
        private List<RouteRule> routeRules = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUserServiceBaseUrl() {
            return userServiceBaseUrl;
        }

        public void setUserServiceBaseUrl(String userServiceBaseUrl) {
            this.userServiceBaseUrl = userServiceBaseUrl;
        }

        public int getQueryTimeoutMs() {
            return queryTimeoutMs;
        }

        public void setQueryTimeoutMs(int queryTimeoutMs) {
            this.queryTimeoutMs = queryTimeoutMs;
        }

        public Cache getCache() {
            return cache;
        }

        public Degrade getDegrade() {
            return degrade;
        }

        public Event getEvent() {
            return event;
        }

        public List<RouteRule> getRouteRules() {
            return routeRules;
        }

        public void setRouteRules(List<RouteRule> routeRules) {
            this.routeRules = routeRules;
        }
    }

    public static class Cache {
        private int ttlSeconds = 30;
        private int staleTtlSeconds = 300;
        private final Redis redis = new Redis();

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getStaleTtlSeconds() {
            return staleTtlSeconds;
        }

        public void setStaleTtlSeconds(int staleTtlSeconds) {
            this.staleTtlSeconds = staleTtlSeconds;
        }

        public Redis getRedis() {
            return redis;
        }
    }

    public static class Degrade {
        private boolean allowWithoutSnapshot = true;

        public boolean isAllowWithoutSnapshot() {
            return allowWithoutSnapshot;
        }

        public void setAllowWithoutSnapshot(boolean allowWithoutSnapshot) {
            this.allowWithoutSnapshot = allowWithoutSnapshot;
        }
    }

    public static class Event {
        private boolean enabled = true;
        private String redisChannel = "shiori.authz.user.changed";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRedisChannel() {
            return redisChannel;
        }

        public void setRedisChannel(String redisChannel) {
            this.redisChannel = redisChannel;
        }
    }

    public static class RouteRule {
        private String pathPattern;
        private String method;
        private String permissionCode;

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getPermissionCode() {
            return permissionCode;
        }

        public void setPermissionCode(String permissionCode) {
            this.permissionCode = permissionCode;
        }
    }

    public static class Redis {
        private boolean enabled = true;
        private String keyPrefix = "authz:snapshot:";
        private int ttlSeconds = 30;
        private int staleTtlSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public int getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getStaleTtlSeconds() {
            return staleTtlSeconds;
        }

        public void setStaleTtlSeconds(int staleTtlSeconds) {
            this.staleTtlSeconds = staleTtlSeconds;
        }
    }
}
