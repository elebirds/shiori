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
import moe.hhm.shiori.common.error.PaymentErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.http.ServiceRequestUris;
import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.order.config.PaymentClientProperties;
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
public class PaymentServiceClient {

    static final String HEADER_INTERNAL_TOKEN = "X-Shiori-Internal-Token";
    static final String INTERNAL_CALLER_ROLE = "ROLE_INTERNAL_ORDER_SERVICE";

    private static final ParameterizedTypeReference<Result<ReserveBalancePaymentSnapshot>> RESERVE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Result<SettleBalancePaymentSnapshot>> SETTLE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Result<ReleaseBalancePaymentSnapshot>> RELEASE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Result<RefundBalancePaymentSnapshot>> REFUND_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final GatewaySignProperties gatewaySignProperties;
    private final ObjectMapper objectMapper;
    private final String internalToken;
    private final String serviceBaseUrl;

    public PaymentServiceClient(RestClient.Builder loadBalancedRestClientBuilder,
                                PaymentClientProperties paymentClientProperties,
                                GatewaySignProperties gatewaySignProperties,
                                ObjectMapper objectMapper) {
        this.restClient = loadBalancedRestClientBuilder.build();
        this.gatewaySignProperties = gatewaySignProperties;
        this.objectMapper = objectMapper;
        this.internalToken = paymentClientProperties.getInternalToken();
        this.serviceBaseUrl = paymentClientProperties.getServiceBaseUrl();
    }

    public ReserveBalancePaymentSnapshot reserveOrderPayment(String orderNo,
                                                            Long buyerUserId,
                                                            Long sellerUserId,
                                                            Long amountCent,
                                                            Long userId,
                                                            List<String> roles) {
        String path = "/api/payment/internal/orders/" + orderNo + "/reserve";
        ReserveBalancePaymentCommand command = new ReserveBalancePaymentCommand(buyerUserId, sellerUserId, amountCent);
        try {
            Result<ReserveBalancePaymentSnapshot> result = restClient.post()
                    .uri(ServiceRequestUris.resolve(serviceBaseUrl, path))
                    .headers(headers -> fillSignedHeaders(headers, "POST", path, null, userId, roles))
                    .body(command)
                    .retrieve()
                    .body(RESERVE_TYPE);
            if (result == null || result.code() != Result.SUCCESS_CODE || result.data() == null) {
                throw mapRemoteFailure(HttpStatus.BAD_REQUEST.value(), result);
            }
            return result.data();
        } catch (RestClientResponseException ex) {
            throw mapRemoteException(ex);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapRuntimeException(ex);
        }
    }

    public SettleBalancePaymentSnapshot settleOrderPayment(String orderNo,
                                                           String operatorType,
                                                           Long operatorUserId,
                                                           Long userId,
                                                           List<String> roles) {
        String path = "/api/payment/internal/orders/" + orderNo + "/settle";
        SettleBalancePaymentCommand command = new SettleBalancePaymentCommand(operatorType, operatorUserId);
        try {
            Result<SettleBalancePaymentSnapshot> result = restClient.post()
                    .uri(ServiceRequestUris.resolve(serviceBaseUrl, path))
                    .headers(headers -> fillSignedHeaders(headers, "POST", path, null, userId, roles))
                    .body(command)
                    .retrieve()
                    .body(SETTLE_TYPE);
            if (result == null || result.code() != Result.SUCCESS_CODE || result.data() == null) {
                throw mapRemoteFailure(HttpStatus.BAD_REQUEST.value(), result);
            }
            return result.data();
        } catch (RestClientResponseException ex) {
            throw mapRemoteException(ex);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapRuntimeException(ex);
        }
    }

    public ReleaseBalancePaymentSnapshot releaseOrderPayment(String orderNo,
                                                             String reason,
                                                             Long userId,
                                                             List<String> roles) {
        String path = "/api/payment/internal/orders/" + orderNo + "/release";
        ReleaseBalancePaymentCommand command = new ReleaseBalancePaymentCommand(reason);
        try {
            Result<ReleaseBalancePaymentSnapshot> result = restClient.post()
                    .uri(ServiceRequestUris.resolve(serviceBaseUrl, path))
                    .headers(headers -> fillSignedHeaders(headers, "POST", path, null, userId, roles))
                    .body(command)
                    .retrieve()
                    .body(RELEASE_TYPE);
            if (result == null || result.code() != Result.SUCCESS_CODE || result.data() == null) {
                throw mapRemoteFailure(HttpStatus.BAD_REQUEST.value(), result);
            }
            return result.data();
        } catch (RestClientResponseException ex) {
            throw mapRemoteException(ex);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapRuntimeException(ex);
        }
    }

    public RefundBalancePaymentSnapshot refundOrderPayment(String orderNo,
                                                           String refundNo,
                                                           String operatorType,
                                                           Long operatorUserId,
                                                           String reason,
                                                           Long userId,
                                                           List<String> roles) {
        String path = "/api/payment/internal/orders/" + orderNo + "/refund";
        RefundBalancePaymentCommand command = new RefundBalancePaymentCommand(refundNo, operatorType, operatorUserId, reason);
        try {
            Result<RefundBalancePaymentSnapshot> result = restClient.post()
                    .uri(ServiceRequestUris.resolve(serviceBaseUrl, path))
                    .headers(headers -> fillSignedHeaders(headers, "POST", path, null, userId, roles))
                    .body(command)
                    .retrieve()
                    .body(REFUND_TYPE);
            if (result == null || result.code() != Result.SUCCESS_CODE || result.data() == null) {
                throw mapRemoteFailure(HttpStatus.BAD_REQUEST.value(), result);
            }
            return result.data();
        } catch (RestClientResponseException ex) {
            throw mapRemoteException(ex);
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
                           Long userId,
                           List<String> roles) {
        String userIdValue = userId == null ? "" : String.valueOf(userId);
        String rolesValue = appendInternalCallerRole(normalizeRoles(roles));
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String canonical = GatewaySignUtils.buildCanonicalString(method, path, rawQuery, userIdValue, rolesValue, ts, nonce);
        String sign = GatewaySignUtils.hmacSha256Hex(gatewaySignProperties.getInternalSecret(), canonical);

        headers.set(GatewaySignVerifyFilter.HEADER_USER_ID, userIdValue);
        headers.set(GatewaySignVerifyFilter.HEADER_USER_ROLES, rolesValue);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_TS, ts);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN, sign);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_NONCE, nonce);
        headers.set(HEADER_INTERNAL_TOKEN, internalToken);
    }

    private String appendInternalCallerRole(String rolesValue) {
        if (!StringUtils.hasText(rolesValue)) {
            return INTERNAL_CALLER_ROLE;
        }
        List<String> roles = java.util.Arrays.stream(rolesValue.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (roles.contains(INTERNAL_CALLER_ROLE)) {
            return String.join(",", roles);
        }
        return rolesValue + "," + INTERNAL_CALLER_ROLE;
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

    private BizException mapRemoteException(RestClientResponseException ex) {
        Result<?> failure = parseFailure(ex.getResponseBodyAsString());
        return mapRemoteFailure(ex.getStatusCode().value(), failure);
    }

    BizException mapRemoteFailure(int statusCode, Result<?> failure) {
        String detail = buildRemoteDetail(statusCode, failure);
        if (failure != null) {
            if (failure.code() == PaymentErrorCode.PAYMENT_BALANCE_NOT_ENOUGH.code()) {
                return new BizException(OrderErrorCode.ORDER_BALANCE_NOT_ENOUGH, HttpStatus.CONFLICT);
            }
            if (failure.code() == PaymentErrorCode.PAYMENT_REFUND_PENDING_FUNDS.code()) {
                return new BizException(OrderErrorCode.ORDER_REFUND_PENDING_FUNDS, HttpStatus.CONFLICT);
            }
            if (failure.code() == CommonErrorCode.UNAUTHORIZED.code()
                    || failure.code() == CommonErrorCode.FORBIDDEN.code()) {
                return new BizException(OrderErrorCode.ORDER_PAYMENT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY, detail);
            }
        }

        if (statusCode == HttpStatus.REQUEST_TIMEOUT.value() || statusCode == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return new BizException(OrderErrorCode.ORDER_PAYMENT_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, detail);
        }
        if (statusCode == HttpStatus.UNAUTHORIZED.value() || statusCode == HttpStatus.FORBIDDEN.value()) {
            return new BizException(OrderErrorCode.ORDER_PAYMENT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY, detail);
        }
        if (statusCode >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            return new BizException(OrderErrorCode.ORDER_PAYMENT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY, detail);
        }
        if (statusCode >= HttpStatus.BAD_REQUEST.value()) {
            return new BizException(OrderErrorCode.ORDER_PAYMENT_RESPONSE_INVALID, HttpStatus.BAD_GATEWAY, detail);
        }
        return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    BizException mapRuntimeException(RuntimeException ex) {
        if (hasCause(ex, SocketTimeoutException.class)) {
            return new BizException(OrderErrorCode.ORDER_PAYMENT_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT);
        }
        if (hasCause(ex, UnknownHostException.class)
                || hasCause(ex, ConnectException.class)
                || hasCause(ex, NoRouteToHostException.class)
                || ex instanceof ResourceAccessException) {
            return new BizException(OrderErrorCode.ORDER_PAYMENT_UNREACHABLE, HttpStatus.SERVICE_UNAVAILABLE);
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
        } catch (JacksonException e) {
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

    private String buildRemoteDetail(int statusCode, Result<?> failure) {
        if (failure == null) {
            return "remoteStatus=" + statusCode;
        }
        String detail = "remoteStatus=" + statusCode
                + ", remoteCode=" + failure.code()
                + ", remoteMessage=" + failure.message();
        if (failure.data() != null) {
            detail += ", remoteData=" + stringifyRemoteData(failure.data());
        }
        return detail;
    }

    private String stringifyRemoteData(Object data) {
        if (data instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException ex) {
            return String.valueOf(data);
        }
    }
}
