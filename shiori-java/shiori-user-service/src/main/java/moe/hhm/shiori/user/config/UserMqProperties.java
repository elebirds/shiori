package moe.hhm.shiori.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "user.mq")
public class UserMqProperties {

    private boolean enabled = true;
    private String eventExchange = "shiori.user.event";
    private String userStatusChangedRoutingKey = "user.status.changed";
    private String userRoleChangedRoutingKey = "user.role.changed";
    private String userPasswordResetRoutingKey = "user.password.reset";

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

    public String getUserStatusChangedRoutingKey() {
        return userStatusChangedRoutingKey;
    }

    public void setUserStatusChangedRoutingKey(String userStatusChangedRoutingKey) {
        this.userStatusChangedRoutingKey = userStatusChangedRoutingKey;
    }

    public String getUserRoleChangedRoutingKey() {
        return userRoleChangedRoutingKey;
    }

    public void setUserRoleChangedRoutingKey(String userRoleChangedRoutingKey) {
        this.userRoleChangedRoutingKey = userRoleChangedRoutingKey;
    }

    public String getUserPasswordResetRoutingKey() {
        return userPasswordResetRoutingKey;
    }

    public void setUserPasswordResetRoutingKey(String userPasswordResetRoutingKey) {
        this.userPasswordResetRoutingKey = userPasswordResetRoutingKey;
    }
}
