package moe.hhm.shiori.order.client;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.http.ServiceRequestUris;
import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.order.config.NotifyClientProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class NotifyChatClient {

    private static final ParameterizedTypeReference<Result<ChatConversationSnapshot>> CONVERSATION_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final GatewaySignProperties gatewaySignProperties;
    private final String serviceBaseUrl;

    public NotifyChatClient(RestClient.Builder loadBalancedRestClientBuilder,
                            NotifyClientProperties notifyClientProperties,
                            GatewaySignProperties gatewaySignProperties) {
        this.restClient = loadBalancedRestClientBuilder.build();
        this.gatewaySignProperties = gatewaySignProperties;
        this.serviceBaseUrl = notifyClientProperties.getServiceBaseUrl();
    }

    public ChatConversationSnapshot getConversationForUser(Long conversationId, Long userId, List<String> roles) {
        String path = "/internal/chat/conversations/" + conversationId;
        try {
            Result<ChatConversationSnapshot> result = restClient.get()
                    .uri(ServiceRequestUris.resolve(serviceBaseUrl, path))
                    .headers(headers -> fillSignedHeaders(headers, "GET", path, null, userId, roles))
                    .retrieve()
                    .body(CONVERSATION_TYPE);
            if (result == null || result.code() != Result.SUCCESS_CODE || result.data() == null) {
                throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
            }
            return result.data();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    || ex.getStatusCode().value() == HttpStatus.FORBIDDEN.value()
                    || ex.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
            }
            throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (hasCause(ex, SocketTimeoutException.class)) {
                throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT);
            }
            if (ex instanceof ResourceAccessException
                    || hasCause(ex, UnknownHostException.class)
                    || hasCause(ex, ConnectException.class)
                    || hasCause(ex, NoRouteToHostException.class)) {
                throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
            }
            throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
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
