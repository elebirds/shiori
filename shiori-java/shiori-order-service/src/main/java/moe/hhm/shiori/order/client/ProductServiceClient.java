package moe.hhm.shiori.order.client;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.order.config.ProductClientProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProductServiceClient {

    private static final ParameterizedTypeReference<Result<ProductDetailSnapshot>> PRODUCT_DETAIL_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Result<StockOperateSnapshot>> STOCK_OPERATE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final GatewaySignProperties gatewaySignProperties;
    private final ObjectMapper objectMapper;

    public ProductServiceClient(RestClient.Builder loadBalancedRestClientBuilder,
                                ProductClientProperties productClientProperties,
                                GatewaySignProperties gatewaySignProperties,
                                ObjectMapper objectMapper) {
        this.restClient = loadBalancedRestClientBuilder
                .baseUrl(productClientProperties.getServiceBaseUrl())
                .build();
        this.gatewaySignProperties = gatewaySignProperties;
        this.objectMapper = objectMapper;
    }

    public ProductDetailSnapshot getProductDetail(Long productId, Long userId, List<String> roles) {
        String path = "/api/product/products/" + productId;
        try {
            Result<ProductDetailSnapshot> result = restClient.get()
                    .uri(path)
                    .headers(headers -> fillSignedHeaders(headers, "GET", path, null, userId, roles))
                    .retrieve()
                    .body(PRODUCT_DETAIL_TYPE);
            if (result == null) {
                throw new BizException(OrderErrorCode.ORDER_PRODUCT_RESPONSE_INVALID, HttpStatus.BAD_GATEWAY);
            }
            if (result.code() != Result.SUCCESS_CODE) {
                throw mapRemoteFailure(HttpStatus.BAD_REQUEST.value(), result);
            }
            if (result.data() == null) {
                throw new BizException(OrderErrorCode.ORDER_PRODUCT_RESPONSE_INVALID, HttpStatus.BAD_GATEWAY);
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

    public StockOperateSnapshot deductStock(Long skuId, Integer quantity, String bizNo, Long userId, List<String> roles) {
        return executeStockOperate("/api/product/internal/stock/deduct",
                new StockDeductCommand(skuId, quantity, bizNo), userId, roles);
    }

    public StockOperateSnapshot releaseStock(Long skuId, Integer quantity, String bizNo, Long userId, List<String> roles) {
        return executeStockOperate("/api/product/internal/stock/release",
                new StockReleaseCommand(skuId, quantity, bizNo), userId, roles);
    }

    void fillSignedHeaders(HttpHeaders headers, String method, String path, String rawQuery, Long userId, List<String> roles) {
        String userIdValue = userId == null ? "" : String.valueOf(userId);
        String rolesValue = normalizeRoles(roles);
        String ts = String.valueOf(System.currentTimeMillis());
        String canonical = GatewaySignUtils.buildCanonicalString(method, path, rawQuery, userIdValue, rolesValue, ts);
        String sign = buildSign(canonical);

        headers.set(GatewaySignVerifyFilter.HEADER_USER_ID, userIdValue);
        headers.set(GatewaySignVerifyFilter.HEADER_USER_ROLES, rolesValue);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_TS, ts);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN, sign);
    }

    String buildSign(String canonical) {
        return GatewaySignUtils.hmacSha256Hex(gatewaySignProperties.getInternalSecret(), canonical);
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

    private StockOperateSnapshot executeStockOperate(String path, Object command, Long userId, List<String> roles) {
        try {
            Result<StockOperateSnapshot> result = restClient.post()
                    .uri(path)
                    .headers(headers -> fillSignedHeaders(headers, "POST", path, null, userId, roles))
                    .body(command)
                    .retrieve()
                    .body(STOCK_OPERATE_TYPE);
            if (result == null) {
                throw new BizException(OrderErrorCode.ORDER_PRODUCT_RESPONSE_INVALID, HttpStatus.BAD_GATEWAY);
            }
            if (result.code() != Result.SUCCESS_CODE) {
                throw mapRemoteFailure(HttpStatus.BAD_REQUEST.value(), result);
            }
            if (result.data() == null) {
                throw new BizException(OrderErrorCode.ORDER_PRODUCT_RESPONSE_INVALID, HttpStatus.BAD_GATEWAY);
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

    private BizException mapRemoteException(RestClientResponseException ex) {
        Result<?> failure = parseFailure(ex.getResponseBodyAsString());
        return mapRemoteFailure(ex.getStatusCode().value(), failure);
    }

    BizException mapRemoteFailure(int statusCode, Result<?> failure) {
        if (failure != null) {
            if (failure.code() == ProductErrorCode.STOCK_NOT_ENOUGH.code()) {
                return new BizException(OrderErrorCode.ORDER_STOCK_NOT_ENOUGH, HttpStatus.CONFLICT);
            }
            if (failure.code() == ProductErrorCode.PRODUCT_NOT_FOUND.code()
                    || failure.code() == ProductErrorCode.SKU_NOT_FOUND.code()
                    || failure.code() == ProductErrorCode.PRODUCT_NOT_ON_SALE.code()) {
                return new BizException(OrderErrorCode.ORDER_PRODUCT_INVALID, HttpStatus.BAD_REQUEST);
            }
            if (failure.code() == CommonErrorCode.UNAUTHORIZED.code()
                    || failure.code() == CommonErrorCode.FORBIDDEN.code()) {
                return new BizException(OrderErrorCode.ORDER_PRODUCT_AUTH_FAILED, HttpStatus.BAD_GATEWAY);
            }
        }

        if (statusCode == HttpStatus.UNAUTHORIZED.value() || statusCode == HttpStatus.FORBIDDEN.value()) {
            return new BizException(OrderErrorCode.ORDER_PRODUCT_AUTH_FAILED, HttpStatus.BAD_GATEWAY);
        }
        if (statusCode == HttpStatus.NOT_FOUND.value()) {
            return new BizException(OrderErrorCode.ORDER_PRODUCT_INVALID, HttpStatus.BAD_REQUEST);
        }
        if (statusCode == HttpStatus.REQUEST_TIMEOUT.value()
                || statusCode == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return new BizException(OrderErrorCode.ORDER_PRODUCT_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT);
        }
        if (statusCode >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            return new BizException(OrderErrorCode.ORDER_PRODUCT_SERVICE_ERROR, HttpStatus.BAD_GATEWAY);
        }
        if (statusCode >= HttpStatus.BAD_REQUEST.value()) {
            return new BizException(OrderErrorCode.ORDER_PRODUCT_RESPONSE_INVALID, HttpStatus.BAD_GATEWAY);
        }
        return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    BizException mapRuntimeException(RuntimeException ex) {
        if (hasCause(ex, SocketTimeoutException.class)) {
            return new BizException(OrderErrorCode.ORDER_PRODUCT_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT);
        }
        if (hasCause(ex, UnknownHostException.class)
                || hasCause(ex, ConnectException.class)
                || hasCause(ex, NoRouteToHostException.class)) {
            return new BizException(OrderErrorCode.ORDER_PRODUCT_UNREACHABLE, HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (ex instanceof ResourceAccessException) {
            return new BizException(OrderErrorCode.ORDER_PRODUCT_UNREACHABLE, HttpStatus.SERVICE_UNAVAILABLE);
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
}
