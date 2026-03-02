package moe.hhm.shiori.common.autoconfigure;

import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.common.security.ServiceSecurityConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.security.web.SecurityFilterChain",
        "org.springframework.security.config.annotation.web.builders.HttpSecurity"
})
@ConditionalOnProperty(prefix = "security.gateway-sign", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GatewaySignProperties.class)
@Import(ServiceSecurityConfiguration.class)
public class GatewayServiceSecurityAutoConfiguration {
}
