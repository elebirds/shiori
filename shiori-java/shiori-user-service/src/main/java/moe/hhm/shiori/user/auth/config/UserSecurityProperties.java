package moe.hhm.shiori.user.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user.security")
public class UserSecurityProperties {

    private int loginFailLockThreshold = 5;
    private long lockMinutes = 15;

    public int getLoginFailLockThreshold() {
        return loginFailLockThreshold;
    }

    public void setLoginFailLockThreshold(int loginFailLockThreshold) {
        this.loginFailLockThreshold = loginFailLockThreshold;
    }

    public long getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(long lockMinutes) {
        this.lockMinutes = lockMinutes;
    }
}
