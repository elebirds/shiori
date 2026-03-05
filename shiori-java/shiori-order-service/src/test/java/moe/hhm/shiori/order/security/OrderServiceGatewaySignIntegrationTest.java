package moe.hhm.shiori.order.security;

import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.gateway-sign.internal-secret=test-gateway-sign-secret-32-bytes-0001",
        "security.gateway-sign.max-skew-seconds=300",
        "order.payment-client.internal-token=test-order-payment-internal-token-000000000001"
})
@AutoConfigureMockMvc
class OrderServiceGatewaySignIntegrationTest {

    private static final String SECRET = "test-gateway-sign-secret-32-bytes-0001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OrderService orderService;

    @Test
    void shouldReturn401WhenSignHeadersMissing() throws Exception {
        mockMvc.perform(get("/api/v2/order/demo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void shouldReturn401WhenTimestampExpired() throws Exception {
        String userId = "1001";
        String roles = "ROLE_USER";
        String ts = String.valueOf(System.currentTimeMillis() - 600_000);
        String nonce = "nonce-expired";
        String sign = sign("GET", "/api/v2/order/demo", null, userId, roles, ts, nonce);
        HttpHeaders headers = signedHeaders(userId, roles, ts, nonce, sign);

        mockMvc.perform(get("/api/v2/order/demo").headers(headers))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void shouldReturn403WhenUserAccessAdminPath() throws Exception {
        String userId = "1001";
        String roles = "ROLE_USER";
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-admin";
        String sign = sign("GET", "/api/admin/demo", null, userId, roles, ts, nonce);
        HttpHeaders headers = signedHeaders(userId, roles, ts, nonce, sign);

        mockMvc.perform(get("/api/admin/demo").headers(headers))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10004));
    }

    @Test
    void shouldReturn403WhenUserAccessV2AdminPath() throws Exception {
        String userId = "1001";
        String roles = "ROLE_USER";
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-admin-v2";
        String sign = sign("GET", "/api/v2/admin/demo", null, userId, roles, ts, nonce);
        HttpHeaders headers = signedHeaders(userId, roles, ts, nonce, sign);

        mockMvc.perform(get("/api/v2/admin/demo").headers(headers))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10004));
    }

    @Test
    void shouldPassSecurityChainWithValidSign() throws Exception {
        String userId = "1001";
        String roles = "ROLE_USER";
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-valid";
        String sign = sign("GET", "/api/v2/order/demo", null, userId, roles, ts, nonce);
        HttpHeaders headers = signedHeaders(userId, roles, ts, nonce, sign);

        mockMvc.perform(get("/api/v2/order/demo").headers(headers))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    @Test
    void shouldReturn401WhenNonceReplayed() throws Exception {
        String userId = "1001";
        String roles = "ROLE_USER";
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-replay";
        String sign = sign("GET", "/api/v2/order/demo", null, userId, roles, ts, nonce);
        HttpHeaders headers = signedHeaders(userId, roles, ts, nonce, sign);

        mockMvc.perform(get("/api/v2/order/demo").headers(headers))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
        mockMvc.perform(get("/api/v2/order/demo").headers(headers))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10003));
    }

    private String sign(String method, String path, String rawQuery, String userId, String userRoles,
                        String ts, String nonce) {
        String canonical = GatewaySignUtils.buildCanonicalString(method, path, rawQuery, userId, userRoles, ts, nonce);
        return GatewaySignUtils.hmacSha256Hex(SECRET, canonical);
    }

    private HttpHeaders signedHeaders(String userId, String userRoles, String ts, String nonce, String sign) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(GatewaySignVerifyFilter.HEADER_USER_ID, userId);
        headers.set(GatewaySignVerifyFilter.HEADER_USER_ROLES, userRoles);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_TS, ts);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_NONCE, nonce);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN, sign);
        return headers;
    }
}
