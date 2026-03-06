package moe.hhm.shiori.order.client;

import java.net.SocketTimeoutException;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignProperties;
import moe.hhm.shiori.order.config.UserClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceClientTest {

    private UserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        GatewaySignProperties gatewaySignProperties = new GatewaySignProperties();
        gatewaySignProperties.setInternalSecret("test-gateway-sign-secret-32-bytes-0001");

        UserClientProperties userClientProperties = new UserClientProperties();
        userClientProperties.setServiceBaseUrl("http://shiori-user-service");

        userServiceClient = new UserServiceClient(
                RestClient.builder(),
                userClientProperties,
                gatewaySignProperties,
                new ObjectMapper()
        );
    }

    @Test
    void shouldMapRemoteNotFoundToAddressNotFound() {
        Result<Object> failure = Result.failure(UserErrorCode.ADDRESS_NOT_FOUND);

        BizException ex = ReflectionTestUtils.invokeMethod(
                userServiceClient,
                "mapRemoteFailure",
                404,
                failure
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_ADDRESS_NOT_FOUND.code());
    }

    @Test
    void shouldMapRuntimeTimeoutToOrderUserTimeout() {
        BizException ex = ReflectionTestUtils.invokeMethod(
                userServiceClient,
                "mapRuntimeException",
                new RuntimeException(new SocketTimeoutException("timeout"))
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_USER_TIMEOUT.code());
    }

    @Test
    void shouldMapResourceAccessToOrderUserUnreachable() {
        BizException ex = ReflectionTestUtils.invokeMethod(
                userServiceClient,
                "mapRuntimeException",
                new ResourceAccessException("connect fail")
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode().code()).isEqualTo(OrderErrorCode.ORDER_USER_UNREACHABLE.code());
    }
}
