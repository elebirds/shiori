package moe.hhm.shiori.common.autoconfigure;

import moe.hhm.shiori.common.security.authz.PermissionGuard;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AuthzGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PermissionGuard permissionGuard() {
        return new PermissionGuard();
    }
}
