package moe.hhm.shiori.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "user.authz")
public class UserAuthzProperties {

    private int snapshotTtlSeconds = 30;

    public int getSnapshotTtlSeconds() {
        return snapshotTtlSeconds;
    }

    public void setSnapshotTtlSeconds(int snapshotTtlSeconds) {
        this.snapshotTtlSeconds = snapshotTtlSeconds;
    }
}
