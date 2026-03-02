package moe.hhm.shiori.gateway.security;

import java.nio.charset.StandardCharsets;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.GatewayErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public GatewayAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = writeBody(Result.failure(GatewayErrorCode.ACCESS_DENIED));
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private byte[] writeBody(Result<Object> result) {
        try {
            return objectMapper.writeValueAsBytes(result);
        } catch (JacksonException e) {
            return "{\"code\":20003,\"message\":\"网关无权限访问\",\"data\":null,\"timestamp\":0}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
