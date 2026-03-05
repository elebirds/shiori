package moe.hhm.shiori.order.client;

import java.net.SocketTimeoutException;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.error.PaymentErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.order.config.PaymentClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceClientTest {

    private static final String TOKEN = "test-order-payment-internal-token-000000000001";
    private PaymentServiceClient client;
    private GatewaySignProperties gatewaySignProperties;

    @BeforeEach
    void setUp() {
        PaymentClientProperties properties = new PaymentClientProperties();
        properties.setInternalToken(TOKEN);
        gatewaySignProperties = new GatewaySignProperties();
        gatewaySignProperties.setInternalSecret("test-gateway-sign-secret-32-bytes-0001");
        client = new PaymentServiceClient(
                RestClient.builder(),
                properties,
                gatewaySignProperties,
                new ObjectMapper()
        );
    }

    @Test
    void shouldWriteSignedHeadersWithInternalToken() {
        HttpHeaders headers = new HttpHeaders();
        client.fillSignedHeaders(headers, "POST", "/api/payment/internal/orders/O001/reserve", null, 1001L, null);

        String ts = headers.getFirst(GatewaySignVerifyFilter.HEADER_GATEWAY_TS);
        String nonce = headers.getFirst(GatewaySignVerifyFilter.HEADER_GATEWAY_NONCE);
        String sign = headers.getFirst(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN);

        assertThat(headers.getFirst(PaymentServiceClient.HEADER_INTERNAL_TOKEN)).isEqualTo(TOKEN);
        assertThat(headers.getFirst(GatewaySignVerifyFilter.HEADER_USER_ID)).isEqualTo("1001");
        assertThat(headers.getFirst(GatewaySignVerifyFilter.HEADER_USER_ROLES)).isEqualTo("ROLE_USER");
        assertThat(ts).isNotBlank();
        assertThat(nonce).isNotBlank();
        assertThat(sign).isNotBlank();

        String canonical = GatewaySignUtils.buildCanonicalString(
                "POST",
                "/api/payment/internal/orders/O001/reserve",
                null,
                "1001",
                "ROLE_USER",
                ts,
                nonce
        );
        assertThat(sign).isEqualTo(GatewaySignUtils.hmacSha256Hex(gatewaySignProperties.getInternalSecret(), canonical));
    }

    @Test
    void shouldMapBalanceNotEnoughToOrderBalanceNotEnough() {
        Result<Object> failure = new Result<>(
                PaymentErrorCode.PAYMENT_BALANCE_NOT_ENOUGH.code(),
                PaymentErrorCode.PAYMENT_BALANCE_NOT_ENOUGH.message(),
                null,
                System.currentTimeMillis()
        );
        BizException ex = client.mapRemoteFailure(HttpStatus.BAD_REQUEST.value(), failure);
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_BALANCE_NOT_ENOUGH.code());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldMapUnauthorizedToPaymentServiceError() {
        BizException ex = client.mapRemoteFailure(HttpStatus.UNAUTHORIZED.value(), null);
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_PAYMENT_SERVICE_ERROR.code());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void shouldMapTimeoutRuntimeToPaymentTimeout() {
        ResourceAccessException runtimeEx = new ResourceAccessException("timeout", new SocketTimeoutException("timeout"));
        BizException ex = client.mapRuntimeException(runtimeEx);
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_PAYMENT_TIMEOUT.code());
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }
}
