package moe.hhm.shiori.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import io.micrometer.core.instrument.MeterRegistry;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.CommonErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@EnableMethodSecurity
public class ServiceSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GatewaySignVerifyFilter gatewaySignVerifyFilter(GatewaySignProperties properties,
                                                           ObjectProvider<ObjectMapper> objectMapperProvider,
                                                           ObjectProvider<MeterRegistry> meterRegistryProvider) {
        if (properties.isEnabled() && !StringUtils.hasText(properties.getInternalSecret())) {
            throw new IllegalStateException("缺少 security.gateway-sign.internal-secret 配置");
        }
        return new GatewaySignVerifyFilter(
                properties,
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                meterRegistryProvider.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain serviceSecurityFilterChain(HttpSecurity http,
                                                          GatewaySignProperties properties,
                                                          GatewaySignVerifyFilter gatewaySignVerifyFilter,
                                                          ObjectProvider<ObjectMapper> objectMapperProvider) throws Exception {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        String[] permitAllPaths = properties.getPermitAllPaths().toArray(String[]::new);
        String[] anonymousGetPaths = properties.getAnonymousGetPaths().toArray(String[]::new);

        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (permitAllPaths.length > 0) {
                        auth.requestMatchers(permitAllPaths).permitAll();
                    }
                    if (anonymousGetPaths.length > 0) {
                        auth.requestMatchers(HttpMethod.GET, anonymousGetPaths).permitAll();
                    }
                    auth.requestMatchers("/api/admin/**", "/api/v2/admin/**").hasRole("ADMIN")
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().permitAll();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeSecurityError(response, objectMapper, CommonErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeSecurityError(response, objectMapper, CommonErrorCode.FORBIDDEN)))
                .addFilterBefore(gatewaySignVerifyFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeSecurityError(jakarta.servlet.http.HttpServletResponse response,
                                    ObjectMapper objectMapper,
                                    CommonErrorCode errorCode) throws IOException {
        response.setStatus(errorCode == CommonErrorCode.FORBIDDEN ? 403 : 401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Object> result = Result.failure(errorCode);
        try {
            response.getWriter().write(objectMapper.writeValueAsString(result));
        } catch (JacksonException e) {
            response.getWriter().write(
                    "{\"code\":" + errorCode.code() + ",\"message\":\"" + errorCode.message()
                            + "\",\"data\":null,\"timestamp\":0}"
            );
        }
    }
}
