package moe.hhm.shiori.social.client;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.social.config.SocialUserClientProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

@Service
public class UserServiceClient {

    private static final int FOLLOWING_PAGE_SIZE = 50;
    private static final int FOLLOWING_FETCH_GUARD = 200;
    private static final ParameterizedTypeReference<Result<List<UserPublicProfileSnapshot>>> PROFILE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Result<FollowUserPageSnapshot>> FOLLOWING_PAGE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final GatewaySignProperties gatewaySignProperties;

    public UserServiceClient(RestClient.Builder loadBalancedRestClientBuilder,
                             SocialUserClientProperties socialUserClientProperties,
                             GatewaySignProperties gatewaySignProperties) {
        this.restClient = loadBalancedRestClientBuilder
                .baseUrl(socialUserClientProperties.getServiceBaseUrl())
                .build();
        this.gatewaySignProperties = gatewaySignProperties;
    }

    public List<Long> listFollowingUserIdsIncludingSelf(Long currentUserId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
        String currentUserNo = resolveUserNoByUserId(currentUserId);
        LinkedHashSet<Long> authorUserIds = new LinkedHashSet<>();
        authorUserIds.add(currentUserId);

        int page = 1;
        for (int guard = 0; guard < FOLLOWING_FETCH_GUARD; guard++) {
            FollowUserPageSnapshot response = fetchFollowingPage(currentUserId, currentUserNo, page);
            List<FollowUserItemSnapshot> items = response.items() == null ? List.of() : response.items();
            for (FollowUserItemSnapshot item : items) {
                if (item.userId() != null && item.userId() > 0) {
                    authorUserIds.add(item.userId());
                }
            }
            int totalPages = Math.max(1, (int) Math.ceil((double) response.total() / FOLLOWING_PAGE_SIZE));
            if (page >= totalPages || items.isEmpty()) {
                break;
            }
            page += 1;
        }
        return new ArrayList<>(authorUserIds);
    }

    private String resolveUserNoByUserId(Long currentUserId) {
        String path = "/api/user/profiles/by-user-ids";
        String rawQuery = "userIds=" + currentUserId;
        try {
            Result<List<UserPublicProfileSnapshot>> result = restClient.get()
                    .uri(path + "?" + rawQuery)
                    .headers(headers -> fillSignedHeaders(headers, "GET", path, rawQuery, currentUserId))
                    .retrieve()
                    .body(PROFILE_LIST_TYPE);
            if (result == null || result.code() != Result.SUCCESS_CODE || result.data() == null || result.data().isEmpty()) {
                throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
            }
            String userNo = result.data().getFirst().userNo();
            if (!StringUtils.hasText(userNo)) {
                throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
            }
            return userNo.trim();
        } catch (RestClientResponseException ex) {
            throw mapRemoteException(ex);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw mapRuntimeException(ex);
        }
    }

    private FollowUserPageSnapshot fetchFollowingPage(Long currentUserId, String currentUserNo, int page) {
        String normalizedUserNo = UriUtils.encodePathSegment(currentUserNo, java.nio.charset.StandardCharsets.UTF_8);
        String path = "/api/user/profiles/" + normalizedUserNo + "/following";
        String rawQuery = "page=" + page + "&size=" + FOLLOWING_PAGE_SIZE;
        try {
            Result<FollowUserPageSnapshot> result = restClient.get()
                    .uri(path + "?" + rawQuery)
                    .headers(headers -> fillSignedHeaders(headers, "GET", path, rawQuery, currentUserId))
                    .retrieve()
                    .body(FOLLOWING_PAGE_TYPE);
            if (result == null || result.code() != Result.SUCCESS_CODE || result.data() == null) {
                throw new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
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

    private void fillSignedHeaders(HttpHeaders headers,
                                   String method,
                                   String path,
                                   String rawQuery,
                                   Long userId) {
        String userIdValue = userId == null ? "" : String.valueOf(userId);
        String rolesValue = "ROLE_USER";
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

    private BizException mapRemoteException(RestClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        if (statusCode == HttpStatus.REQUEST_TIMEOUT.value() || statusCode == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT);
        }
        return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
    }

    private BizException mapRuntimeException(RuntimeException ex) {
        if (hasCause(ex, SocketTimeoutException.class)) {
            return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT);
        }
        if (hasCause(ex, UnknownHostException.class)
                || hasCause(ex, ConnectException.class)
                || hasCause(ex, NoRouteToHostException.class)) {
            return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (ex instanceof ResourceAccessException) {
            return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
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

    private record UserPublicProfileSnapshot(
            Long userId,
            String userNo
    ) {
    }

    private record FollowUserPageSnapshot(
            long total,
            int page,
            int size,
            List<FollowUserItemSnapshot> items
    ) {
    }

    private record FollowUserItemSnapshot(
            Long userId,
            String userNo,
            String nickname,
            String avatarUrl,
            String bio,
            String followedAt
    ) {
    }
}
