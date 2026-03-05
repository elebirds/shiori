package moe.hhm.shiori.order.controller.v2;

import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.gateway-sign.internal-secret=test-gateway-sign-secret-32-bytes-0001",
        "security.gateway-sign.max-skew-seconds=300"
})
@AutoConfigureMockMvc
class OrderV2ControllerMvcTest {

    private static final String SECRET = "test-gateway-sign-secret-32-bytes-0001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OrderService orderService;

    @Test
    void shouldPayByBalanceWhenBodyAbsent() throws Exception {
        when(orderCommandService.payOrderByBalance(1001L, "O001", "idem-v2-pay-1"))
                .thenReturn(new OrderOperateResponse("O001", "PAID", false));

        HttpHeaders headers = signedHeaders("POST", "/api/v2/order/orders/O001/pay", null, "1001", "ROLE_USER");
        headers.set("Idempotency-Key", "idem-v2-pay-1");

        mockMvc.perform(post("/api/v2/order/orders/O001/pay").headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O001"))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    void shouldReturn400WhenV2PayBodyProvided() throws Exception {
        HttpHeaders headers = signedHeaders("POST", "/api/v2/order/orders/O001/pay", null, "1001", "ROLE_USER");
        headers.set("Idempotency-Key", "idem-v2-pay-2");
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/v2/order/orders/O001/pay")
                        .headers(headers)
                        .content("{\"paymentNo\":\"PAY-001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void shouldReturn400WhenV2PayIdempotencyKeyMissing() throws Exception {
        HttpHeaders headers = signedHeaders("POST", "/api/v2/order/orders/O001/pay", null, "1001", "ROLE_USER");

        mockMvc.perform(post("/api/v2/order/orders/O001/pay").headers(headers))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    private HttpHeaders signedHeaders(String method, String path, String rawQuery, String userId, String roles) {
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-" + System.nanoTime();
        String canonical = GatewaySignUtils.buildCanonicalString(method, path, rawQuery, userId, roles, ts, nonce);
        String sign = GatewaySignUtils.hmacSha256Hex(SECRET, canonical);

        HttpHeaders headers = new HttpHeaders();
        headers.set(GatewaySignVerifyFilter.HEADER_USER_ID, userId);
        headers.set(GatewaySignVerifyFilter.HEADER_USER_ROLES, roles);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_TS, ts);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_NONCE, nonce);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN, sign);
        return headers;
    }
}
