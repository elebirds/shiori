package moe.hhm.shiori.order.controller;

import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.gateway-sign.internal-secret=test-gateway-sign-secret-32-bytes-0001",
        "security.gateway-sign.max-skew-seconds=300",
        "order.payment-client.internal-token=test-order-payment-internal-token-000000000001"
})
@AutoConfigureMockMvc
class OrderControllerMvcTest {

    private static final String SECRET = "test-gateway-sign-secret-32-bytes-0001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OrderService orderService;

    @Test
    void shouldCreateOrderWithIdempotencyKey() throws Exception {
        when(orderCommandService.createOrder(anyLong(), anyList(), anyString(), any()))
                .thenReturn(new CreateOrderResponse("O202603030001", "UNPAID", 2600L, 3, false));

        HttpHeaders headers = signedHeaders("POST", "/api/order/orders", null, "1001", "ROLE_USER");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-create-001");

        mockMvc.perform(post("/api/order/orders")
                        .headers(headers)
                        .content("""
                                {"items":[{"productId":1,"skuId":11,"quantity":1}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O202603030001"));
    }

    @Test
    void shouldReturn400WhenIdempotencyKeyMissing() throws Exception {
        HttpHeaders headers = signedHeaders("POST", "/api/order/orders", null, "1001", "ROLE_USER");
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/order/orders")
                        .headers(headers)
                        .content("""
                                {"items":[{"productId":1,"skuId":11,"quantity":1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void shouldReturn400WhenPayIdempotencyKeyMissing() throws Exception {
        HttpHeaders headers = signedHeaders("POST", "/api/order/orders/O001/pay", null, "1001", "ROLE_USER");
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/order/orders/O001/pay")
                        .headers(headers)
                        .content("""
                                {"paymentNo":"PAY-001"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void shouldReturn400WhenCancelIdempotencyKeyMissing() throws Exception {
        HttpHeaders headers = signedHeaders("POST", "/api/order/orders/O001/cancel", null, "1001", "ROLE_USER");
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/order/orders/O001/cancel")
                        .headers(headers)
                        .content("""
                                {"reason":"manual"}
                                """))
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
