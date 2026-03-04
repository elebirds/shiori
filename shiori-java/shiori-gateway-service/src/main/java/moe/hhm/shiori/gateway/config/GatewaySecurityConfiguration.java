package moe.hhm.shiori.gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import moe.hhm.shiori.gateway.security.GatewayAccessDeniedHandler;
import moe.hhm.shiori.gateway.security.GatewayAuthenticationEntryPoint;
import moe.hhm.shiori.gateway.security.JwtClaimUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         GatewaySecurityProperties properties,
                                                         GatewayAuthenticationEntryPoint authenticationEntryPoint,
                                                         GatewayAccessDeniedHandler accessDeniedHandler) {
        String[] whitelist = properties.getAuth().getWhitelist().toArray(String[]::new);
        String[] anonymousGetPaths = properties.getAuth().getAnonymousGetPaths().toArray(String[]::new);
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(authorize -> {
                    if (whitelist.length > 0) {
                        authorize.pathMatchers(whitelist).permitAll();
                    }
                    if (anonymousGetPaths.length > 0) {
                        authorize.pathMatchers(HttpMethod.GET, anonymousGetPaths).permitAll();
                    }
                    authorize.pathMatchers("/api/admin/**", "/api/v2/admin/**").hasRole("ADMIN")
                            .pathMatchers("/api/**").authenticated()
                            .anyExchange().permitAll();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder(properties))
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public GatewayAuthenticationEntryPoint gatewayAuthenticationEntryPoint(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new GatewayAuthenticationEntryPoint(objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    public GatewayAccessDeniedHandler gatewayAccessDeniedHandler(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new GatewayAccessDeniedHandler(objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    public NimbusReactiveJwtDecoder jwtDecoder(GatewaySecurityProperties properties) {
        String secret = properties.getJwt().getHmacSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("缺少 security.jwt.hmac-secret 配置");
        }
        SecretKey secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();
        decoder.setJwtValidator(buildJwtValidator(properties.getJwt().getIssuer()));
        return decoder;
    }

    @Bean
    public org.springframework.core.convert.converter.Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return jwt -> {
            String principal = JwtClaimUtils.resolveUserId(jwt);
            if (!StringUtils.hasText(principal)) {
                principal = "unknown";
            }

            List<GrantedAuthority> authorities = JwtClaimUtils.normalizeRoles(jwt.getClaim("roles")).stream()
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();
            return Mono.just(new JwtAuthenticationToken(jwt, authorities, principal));
        };
    }

    private OAuth2TokenValidator<Jwt> buildJwtValidator(String issuer) {
        OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();
        if (!StringUtils.hasText(issuer)) {
            return defaultValidator;
        }

        OAuth2TokenValidator<Jwt> issuerValidator = token -> {
            String tokenIssuer = token.getClaimAsString("iss");
            if (issuer.equals(tokenIssuer)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error("invalid_token", "token issuer 无效", null);
            return OAuth2TokenValidatorResult.failure(error);
        };
        return new DelegatingOAuth2TokenValidator<>(defaultValidator, issuerValidator);
    }
}
