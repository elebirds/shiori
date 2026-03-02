package moe.hhm.shiori.gateway.security;

import java.nio.charset.StandardCharsets;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.GatewayErrorCode;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class GatewayAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public GatewayAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = writeBody(Result.failure(GatewayErrorCode.AUTH_REQUIRED));
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private byte[] writeBody(Result<Object> result) {
        try {
            return objectMapper.writeValueAsBytes(result);
        } catch (JacksonException e) {
            return "{\"code\":20002,\"message\":\"网关认证失败\",\"data\":null,\"timestamp\":0}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
