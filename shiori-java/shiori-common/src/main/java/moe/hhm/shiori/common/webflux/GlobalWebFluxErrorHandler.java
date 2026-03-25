package moe.hhm.shiori.common.webflux;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.ErrorCode;
import moe.hhm.shiori.common.error.GatewayErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class GlobalWebFluxErrorHandler implements ErrorWebExceptionHandler, Ordered {
    private static final Logger log = LoggerFactory.getLogger(GlobalWebFluxErrorHandler.class);
    private static final String SECURITY_AUTHENTICATION_EXCEPTION =
            "org.springframework.security.core.AuthenticationException";
    private static final String SECURITY_ACCESS_DENIED_EXCEPTION =
            "org.springframework.security.access.AccessDeniedException";

    private final ObjectMapper objectMapper;

    public GlobalWebFluxErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return -2;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        ErrorResponse errorResponse = resolve(ex);
        exchange.getResponse().setStatusCode(errorResponse.status());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<Object> result = Result.failure(errorResponse.errorCode().code(), errorResponse.errorCode().message(),
                errorResponse.data());

        byte[] bytes = writeBytes(result);
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private ErrorResponse resolve(Throwable ex) {
        if (ex instanceof BizException bizException) {
            return new ErrorResponse(bizException.getHttpStatus(), bizException.getErrorCode(), bizException.getExtraData());
        }

        if (ex instanceof ServerWebInputException inputException) {
            return new ErrorResponse(HttpStatus.BAD_REQUEST, GatewayErrorCode.INVALID_REQUEST, inputException.getReason());
        }

        if (hasCauseOfType(ex, SECURITY_AUTHENTICATION_EXCEPTION)) {
            return new ErrorResponse(HttpStatus.UNAUTHORIZED, GatewayErrorCode.AUTH_REQUIRED, null);
        }

        if (hasCauseOfType(ex, SECURITY_ACCESS_DENIED_EXCEPTION)) {
            return new ErrorResponse(HttpStatus.FORBIDDEN, GatewayErrorCode.ACCESS_DENIED, null);
        }

        if (ex instanceof ResponseStatusException responseStatusException) {
            HttpStatusCode statusCode = responseStatusException.getStatusCode();
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            return new ErrorResponse(status, mapGatewayErrorCode(status), responseStatusException.getReason());
        }

        log.error("网关未捕获异常", ex);

        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, GatewayErrorCode.INTERNAL_ERROR,
                buildInternalErrorDetail(ex));
    }

    private GatewayErrorCode mapGatewayErrorCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST, METHOD_NOT_ALLOWED -> GatewayErrorCode.INVALID_REQUEST;
            case UNAUTHORIZED -> GatewayErrorCode.AUTH_REQUIRED;
            case FORBIDDEN -> GatewayErrorCode.ACCESS_DENIED;
            case NOT_FOUND -> GatewayErrorCode.ROUTE_NOT_FOUND;
            case TOO_MANY_REQUESTS -> GatewayErrorCode.RATE_LIMITED;
            case SERVICE_UNAVAILABLE -> GatewayErrorCode.UPSTREAM_UNAVAILABLE;
            default -> GatewayErrorCode.INTERNAL_ERROR;
        };
    }

    private byte[] writeBytes(Result<Object> result) {
        try {
            return objectMapper.writeValueAsBytes(result);
        } catch (JacksonException e) {
            return "{\"code\":29999,\"message\":\"网关内部错误\",\"data\":null,\"timestamp\":0}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private boolean hasCauseOfType(Throwable ex, String typeName) {
        Class<?> exceptionType = resolveClass(typeName);
        if (exceptionType == null) {
            return false;
        }

        Throwable current = ex;
        while (current != null) {
            if (exceptionType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Class<?> resolveClass(String className) {
        try {
            return Class.forName(className, false, GlobalWebFluxErrorHandler.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private String buildInternalErrorDetail(Throwable ex) {
        if (ex == null) {
            return "unknown";
        }
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + message;
    }

    private record ErrorResponse(HttpStatus status, ErrorCode errorCode, Object data) {
    }
}
