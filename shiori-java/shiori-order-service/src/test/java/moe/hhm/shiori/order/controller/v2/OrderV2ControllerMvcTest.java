package moe.hhm.shiori.order.controller.v2;

import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.order.dto.OrderDetailResponse;
import moe.hhm.shiori.order.dto.OrderItemResponse;
import moe.hhm.shiori.order.dto.OrderShippingAddressResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderConfirmSettlementWorkflowService;
import moe.hhm.shiori.order.service.OrderCartService;
import moe.hhm.shiori.order.service.OrderCreateWorkflowService;
import moe.hhm.shiori.order.service.OrderPayWorkflowService;
import moe.hhm.shiori.order.service.OrderReviewService;
import moe.hhm.shiori.order.service.OrderRefundService;
import moe.hhm.shiori.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.gateway-sign.internal-secret=test-gateway-sign-secret-32-bytes-0001",
        "security.gateway-sign.max-skew-seconds=300",
        "order.payment-client.internal-token=test-order-payment-internal-token-000000000001",
        "order.command.enabled=false",
        "order.timeout-scheduler.enabled=false"
})
@AutoConfigureMockMvc
class OrderV2ControllerMvcTest {

    private static final String SECRET = "test-gateway-sign-secret-32-bytes-0001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OrderCreateWorkflowService orderCreateWorkflowService;

    @MockitoBean
    private OrderPayWorkflowService orderPayWorkflowService;

    @MockitoBean
    private OrderConfirmSettlementWorkflowService orderConfirmSettlementWorkflowService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderCartService orderCartService;

    @MockitoBean
    private OrderReviewService orderReviewService;

    @MockitoBean
    private OrderRefundService orderRefundService;

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

    @Test
    void shouldUpdateOrderFulfillment() throws Exception {
        when(orderCommandService.updateOrderFulfillmentByBuyer(
                1001L,
                List.of("ROLE_USER"),
                "O001",
                new moe.hhm.shiori.order.dto.v2.UpdateOrderFulfillmentRequest("DELIVERY", 101L)
        )).thenReturn(new OrderOperateResponse("O001", "UNPAID", false));
        when(orderService.getOrderDetail(1001L, false, "O001"))
                .thenReturn(orderDetail("DELIVERY"));

        HttpHeaders headers = signedHeaders("PUT", "/api/v2/order/orders/O001/fulfillment", null, "1001", "ROLE_USER");
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(put("/api/v2/order/orders/O001/fulfillment")
                        .headers(headers)
                        .content("{\"fulfillmentMode\":\"DELIVERY\",\"addressId\":101}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O001"))
                .andExpect(jsonPath("$.data.fulfillmentMode").value("DELIVERY"))
                .andExpect(jsonPath("$.data.shippingAddress.receiverName").value("张三"));
    }

    @Test
    void shouldReturn400WhenFulfillmentModeLengthInvalid() throws Exception {
        HttpHeaders headers = signedHeaders("PUT", "/api/v2/order/orders/O001/fulfillment", null, "1001", "ROLE_USER");
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(put("/api/v2/order/orders/O001/fulfillment")
                        .headers(headers)
                        .content("{\"fulfillmentMode\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    private OrderDetailResponse orderDetail(String fulfillmentMode) {
        return new OrderDetailResponse(
                "O001",
                1001L,
                2001L,
                "UNPAID",
                2399L,
                null,
                null,
                null,
                true,
                true,
                fulfillmentMode,
                new OrderShippingAddressResponse(
                        "张三",
                        "13800138000",
                        "广东省",
                        "深圳市",
                        "南山区",
                        "科技园 1 号"
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new OrderItemResponse(1L, "P001", 11L, "S001", "SKU", "{}", 2399L, 1, 2399L)),
                false,
                false,
                false,
                false,
                null
        );
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
