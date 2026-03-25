package moe.hhm.shiori.user.payment.client;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.UUID;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.user.config.UserPaymentClientProperties;
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
public class PaymentWalletClient {

    static final String HEADER_INTERNAL_TOKEN = "X-Shiori-Internal-Token";
    static final String INTERNAL_CALLER_ROLE = "ROLE_INTERNAL_USER_SERVICE";

    private static final ParameterizedTypeReference<Result<InitWalletAccountSnapshot>> INIT_WALLET_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final GatewaySignProperties gatewaySignProperties;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public PaymentWalletClient(RestClient.Builder loadBalancedRestClientBuilder,
                               UserPaymentClientProperties userPaymentClientProperties,
                               GatewaySignProperties gatewaySignProperties,
                               ObjectMapper objectMapper) {
        this.restClient = loadBalancedRestClientBuilder
                .baseUrl(userPaymentClientProperties.getServiceBaseUrl())
                .build();
        this.gatewaySignProperties = gatewaySignProperties;
        this.objectMapper = objectMapper;
        this.internalToken = userPaymentClientProperties.getInternalToken();
    }

    public void initWalletAccount(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new IllegalStateException("user.payment-client.internal-token 未配置");
        }
        String path = "/api/payment/internal/wallets/" + userId + "/init";
        try {
            Result<InitWalletAccountSnapshot> result = restClient.post()
                    .uri(path)
                    .headers(headers -> fillSignedHeaders(headers, "POST", path, null, userId))
                    .retrieve()
                    .body(INIT_WALLET_TYPE);
            if (result == null || result.code() != Result.SUCCESS_CODE || result.data() == null) {
                throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY, "钱包初始化失败");
            }
        } catch (RestClientResponseException ex) {
            throw mapRemoteFailure(ex.getStatusCode().value(), parseFailure(ex.getResponseBodyAsString()));
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapRuntimeException(ex);
        }
    }

    void fillSignedHeaders(HttpHeaders headers,
                           String method,
                           String path,
                           String rawQuery,
                           Long userId) {
        String userIdValue = userId == null ? "" : String.valueOf(userId);
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String canonical = GatewaySignUtils.buildCanonicalString(
                method,
                path,
                rawQuery,
                userIdValue,
                INTERNAL_CALLER_ROLE,
                ts,
                nonce
        );
        String sign = GatewaySignUtils.hmacSha256Hex(gatewaySignProperties.getInternalSecret(), canonical);

        headers.set(GatewaySignVerifyFilter.HEADER_USER_ID, userIdValue);
        headers.set(GatewaySignVerifyFilter.HEADER_USER_ROLES, INTERNAL_CALLER_ROLE);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_TS, ts);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN, sign);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_NONCE, nonce);
        headers.set(HEADER_INTERNAL_TOKEN, internalToken);
    }

    private BizException mapRemoteFailure(int statusCode, Result<?> failure) {
        if (statusCode == HttpStatus.REQUEST_TIMEOUT.value()
                || statusCode == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT, "钱包初始化超时");
        }
        if (statusCode >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY, "钱包初始化失败");
        }
        String message = failure == null || !StringUtils.hasText(failure.message())
                ? "钱包初始化失败"
                : failure.message();
        return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY, message);
    }

    private BizException mapRuntimeException(RuntimeException ex) {
        if (hasCause(ex, SocketTimeoutException.class)) {
            return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT, "钱包初始化超时");
        }
        if (hasCause(ex, UnknownHostException.class)
                || hasCause(ex, ConnectException.class)
                || hasCause(ex, NoRouteToHostException.class)
                || ex instanceof ResourceAccessException) {
            return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "钱包服务不可达");
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

    private record InitWalletAccountSnapshot(Long userId, String status, boolean idempotent) {
    }
}
