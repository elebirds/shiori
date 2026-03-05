package moe.hhm.shiori.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "user.authz")
public class UserAuthzProperties {

    private int snapshotTtlSeconds = 30;
    private final Event event = new Event();

    public int getSnapshotTtlSeconds() {
        return snapshotTtlSeconds;
    }

    public void setSnapshotTtlSeconds(int snapshotTtlSeconds) {
        this.snapshotTtlSeconds = snapshotTtlSeconds;
    }

    public Event getEvent() {
        return event;
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
}
