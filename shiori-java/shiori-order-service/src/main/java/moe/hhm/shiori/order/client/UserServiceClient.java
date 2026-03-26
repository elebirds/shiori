package moe.hhm.shiori.order.client;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.http.ServiceRequestUris;
import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.order.config.UserClientProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class UserServiceClient {

    private static final ParameterizedTypeReference<Result<UserAddressSnapshot>> USER_ADDRESS_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final GatewaySignProperties gatewaySignProperties;
    private final ObjectMapper objectMapper;
    private final String serviceBaseUrl;

    public UserServiceClient(RestClient.Builder loadBalancedRestClientBuilder,
                             UserClientProperties userClientProperties,
                             GatewaySignProperties gatewaySignProperties,
                             ObjectMapper objectMapper) {
        this.restClient = loadBalancedRestClientBuilder.build();
        this.gatewaySignProperties = gatewaySignProperties;
        this.objectMapper = objectMapper;
        this.serviceBaseUrl = userClientProperties.getServiceBaseUrl();
    }

    public UserAddressSnapshot getMyAddress(Long addressId, Long userId, List<String> roles) {
        String path = "/api/user/me/addresses/" + addressId;
        try {
            Result<UserAddressSnapshot> result = restClient.get()
                    .uri(ServiceRequestUris.resolve(serviceBaseUrl, path))
                    .headers(headers -> fillSignedHeaders(headers, "GET", path, null, userId, roles))
                    .retrieve()
                    .body(USER_ADDRESS_TYPE);
            if (result == null || result.code() != Result.SUCCESS_CODE || result.data() == null) {
                throw new BizException(OrderErrorCode.ORDER_USER_RESPONSE_INVALID, HttpStatus.BAD_GATEWAY);
            }
            return result.data();
        } catch (RestClientResponseException ex) {
            throw mapRemoteFailure(ex.getStatusCode().value(), parseFailure(ex.getResponseBodyAsString()));
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapRuntimeException(ex);
        }
    }

    void fillSignedHeaders(HttpHeaders headers, String method, String path, String rawQuery, Long userId, List<String> roles) {
        String userIdValue = userId == null ? "" : String.valueOf(userId);
        String rolesValue = normalizeRoles(roles);
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String canonical = GatewaySignUtils.buildCanonicalString(method, path, rawQuery, userIdValue, rolesValue, ts, nonce);
        String sign = GatewaySignUtils.hmacSha256Hex(gatewaySignProperties.getInternalSecret(), canonical);

        headers.set(GatewaySignVerifyFilter.HEADER_USER_ID, userIdValue);
        headers.set(GatewaySignVerifyFilter.HEADER_USER_ROLES, rolesValue);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_TS, ts);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN, sign);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_NONCE, nonce);
    }

    private String normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "ROLE_USER";
        }
        return roles.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("ROLE_USER");
    }

    private BizException mapRemoteFailure(int statusCode, Result<?> failure) {
        if (failure != null && failure.code() == UserErrorCode.ADDRESS_NOT_FOUND.code()) {
            return new BizException(OrderErrorCode.ORDER_ADDRESS_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
        if (statusCode == HttpStatus.NOT_FOUND.value()) {
            return new BizException(OrderErrorCode.ORDER_ADDRESS_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
        if (statusCode == HttpStatus.REQUEST_TIMEOUT.value()
                || statusCode == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return new BizException(OrderErrorCode.ORDER_USER_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT);
        }
        if (statusCode >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            return new BizException(OrderErrorCode.ORDER_USER_SERVICE_ERROR, HttpStatus.BAD_GATEWAY);
        }
        return new BizException(OrderErrorCode.ORDER_USER_RESPONSE_INVALID, HttpStatus.BAD_GATEWAY);
    }

    private BizException mapRuntimeException(RuntimeException ex) {
        if (hasCause(ex, SocketTimeoutException.class)) {
            return new BizException(OrderErrorCode.ORDER_USER_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT);
        }
        if (hasCause(ex, UnknownHostException.class)
                || hasCause(ex, ConnectException.class)
                || hasCause(ex, NoRouteToHostException.class)) {
            return new BizException(OrderErrorCode.ORDER_USER_UNREACHABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (ex instanceof ResourceAccessException) {
            return new BizException(OrderErrorCode.ORDER_USER_UNREACHABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private Result<?> parseFailure(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readValue(body, new TypeReference<Result<Object>>() {
            });
        } catch (JacksonException ex) {
            return null;
        }
    }

    private boolean hasCause(Throwable ex, Class<? extends Throwable> targetType) {
        Throwable current = ex;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
