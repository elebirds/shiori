package moe.hhm.shiori.common.webflux;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import moe.hhm.shiori.common.error.GatewayErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalWebFluxErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnRouteNotFoundFor404() throws Exception {
        GlobalWebFluxErrorHandler handler = new GlobalWebFluxErrorHandler(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/missing").build());

        StepVerifier.create(handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(1)));
        assertThat(body.get("code").asInt()).isEqualTo(GatewayErrorCode.ROUTE_NOT_FOUND.code());
    }

    @Test
    void shouldReturnInvalidRequestForInputException() throws Exception {
        GlobalWebFluxErrorHandler handler = new GlobalWebFluxErrorHandler(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/bad").build());

        StepVerifier.create(handler.handle(exchange, new ServerWebInputException("invalid input")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(1)));
        assertThat(body.get("code").asInt()).isEqualTo(GatewayErrorCode.INVALID_REQUEST.code());
    }

    @Test
    void shouldReturnAuthRequiredForAuthenticationException() throws Exception {
        GlobalWebFluxErrorHandler handler = new GlobalWebFluxErrorHandler(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/auth").build());

        StepVerifier.create(handler.handle(exchange, new BadCredentialsException("bad credentials")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(1)));
        assertThat(body.get("code").asInt()).isEqualTo(GatewayErrorCode.AUTH_REQUIRED.code());
    }
}
