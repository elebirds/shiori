package moe.hhm.shiori.order.client;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.config.ProductClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ProductServiceClientMappingTest {

    private ProductServiceClient client;

    @BeforeEach
    void setUp() {
        ProductClientProperties properties = new ProductClientProperties();
        GatewaySignProperties gatewaySignProperties = new GatewaySignProperties();
        gatewaySignProperties.setInternalSecret("test-gateway-sign-secret-32-bytes-0001");
        client = new ProductServiceClient(
                RestClient.builder(),
                properties,
                gatewaySignProperties,
                new ObjectMapper()
        );
    }

    @Test
    void shouldMapUnauthorizedStatusToProductAuthFailed() {
        BizException ex = client.mapRemoteFailure(HttpStatus.UNAUTHORIZED.value(), null);
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_PRODUCT_AUTH_FAILED.code());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldMapUnauthorizedBusinessCodeToProductAuthFailed() {
        Result<Object> failure = new Result<>(
                CommonErrorCode.UNAUTHORIZED.code(),
                CommonErrorCode.UNAUTHORIZED.message(),
                null,
                System.currentTimeMillis()
        );
        BizException ex = client.mapRemoteFailure(HttpStatus.BAD_REQUEST.value(), failure);
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_PRODUCT_AUTH_FAILED.code());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldMapServerErrorToProductServiceError() {
        BizException ex = client.mapRemoteFailure(HttpStatus.BAD_GATEWAY.value(), null);
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_PRODUCT_SERVICE_ERROR.code());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldMapBadRequestToProductResponseInvalid() {
        BizException ex = client.mapRemoteFailure(HttpStatus.BAD_REQUEST.value(), null);
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_PRODUCT_RESPONSE_INVALID.code());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldMapStockNotEnoughBusinessCode() {
        Result<Object> failure = new Result<>(
                ProductErrorCode.STOCK_NOT_ENOUGH.code(),
                ProductErrorCode.STOCK_NOT_ENOUGH.message(),
                null,
                System.currentTimeMillis()
        );
        BizException ex = client.mapRemoteFailure(HttpStatus.BAD_REQUEST.value(), failure);
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_STOCK_NOT_ENOUGH.code());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldMapUnknownHostToProductUnreachable() {
        ResourceAccessException runtimeEx = new ResourceAccessException("unreachable", new UnknownHostException("product"));
        BizException ex = client.mapRuntimeException(runtimeEx);
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_PRODUCT_UNREACHABLE.code());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldMapSocketTimeoutToProductTimeout() {
        ResourceAccessException runtimeEx = new ResourceAccessException("timeout", new SocketTimeoutException("timeout"));
        BizException ex = client.mapRuntimeException(runtimeEx);
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_PRODUCT_TIMEOUT.code());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void shouldUseDirectClientForLocalAndIpHost() {
        assertThat(ProductServiceClient.shouldUseDirectClient("http://127.0.0.1:8082")).isTrue();
        assertThat(ProductServiceClient.shouldUseDirectClient("http://localhost:8082")).isTrue();
        assertThat(ProductServiceClient.shouldUseDirectClient("http://192.168.1.10:8082")).isTrue();
        assertThat(ProductServiceClient.shouldUseDirectClient("http://product.internal:8082")).isTrue();
    }

    @Test
    void shouldUseLoadBalancerClientForServiceName() {
        assertThat(ProductServiceClient.shouldUseDirectClient("http://shiori-product-service")).isFalse();
        assertThat(ProductServiceClient.shouldUseDirectClient("http://product-service")).isFalse();
    }
}
