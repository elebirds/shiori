package moe.hhm.shiori.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.CommonErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@EnableMethodSecurity
public class ServiceSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GatewaySignVerifyFilter gatewaySignVerifyFilter(GatewaySignProperties properties,
                                                           ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new GatewaySignVerifyFilter(properties, objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain serviceSecurityFilterChain(HttpSecurity http,
                                                          GatewaySignProperties properties,
                                                          GatewaySignVerifyFilter gatewaySignVerifyFilter,
                                                          ObjectProvider<ObjectMapper> objectMapperProvider) throws Exception {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        String[] permitAllPaths = properties.getPermitAllPaths().toArray(String[]::new);

        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(permitAllPaths).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
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
