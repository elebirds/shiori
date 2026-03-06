package moe.hhm.shiori.payment.controller;

import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.payment.dto.internal.RefundOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.ReserveOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.SettleOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.ReleaseOrderPaymentResponse;
import moe.hhm.shiori.payment.service.PaymentService;
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
        "security.gateway-sign.max-skew-seconds=300",
        "payment.internal-api.token=test-order-payment-internal-token-000000000001",
        "payment.internal-api.require-token=true",
        "payment.outbox.enabled=false",
        "payment.mq.enabled=false",
        "payment.reconcile.enabled=false"
})
@AutoConfigureMockMvc
class InternalPaymentOrderControllerTest {

    private static final String SECRET = "test-gateway-sign-secret-32-bytes-0001";
    private static final String INTERNAL_TOKEN = "test-order-payment-internal-token-000000000001";
    private static final String INTERNAL_ROLE = "ROLE_INTERNAL_ORDER_SERVICE";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void shouldRejectReserveWhenInternalTokenMissing() throws Exception {
        HttpHeaders headers = signedHeaders("POST", "/api/payment/internal/orders/O001/reserve", null, "1001", "ROLE_USER");
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/payment/internal/orders/O001/reserve")
                        .headers(headers)
                        .content("""
                                {"buyerUserId":1001,"sellerUserId":2001,"amountCent":100}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10004));
    }

    @Test
    void shouldRejectSettleWhenInternalTokenInvalid() throws Exception {
        HttpHeaders headers = signedHeaders("POST", "/api/payment/internal/orders/O001/settle", null, "1001", "ROLE_USER");
        headers.set(InternalPaymentOrderController.HEADER_INTERNAL_TOKEN, "invalid-token");
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/payment/internal/orders/O001/settle")
                        .headers(headers)
                        .content("""
                                {"operatorType":"BUYER","operatorUserId":1001}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10004));
    }

    @Test
    void shouldRejectWhenInternalRoleMissing() throws Exception {
        HttpHeaders headers = signedHeaders("POST", "/api/payment/internal/orders/O001/release", null, "1001", "ROLE_USER");
        headers.set(InternalPaymentOrderController.HEADER_INTERNAL_TOKEN, INTERNAL_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/payment/internal/orders/O001/release")
                        .headers(headers)
                        .content("""
                                {"reason":"manual"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10004));
    }

    @Test
    void shouldAllowReleaseWhenInternalTokenValid() throws Exception {
        when(paymentService.releaseOrderPayment("O001", "manual"))
                .thenReturn(new ReleaseOrderPaymentResponse("O001", "P001", "RELEASED", false));

        HttpHeaders headers = signedHeaders("POST", "/api/payment/internal/orders/O001/release", null, "1001", INTERNAL_ROLE);
        headers.set(InternalPaymentOrderController.HEADER_INTERNAL_TOKEN, INTERNAL_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/payment/internal/orders/O001/release")
                        .headers(headers)
                        .content("""
                                {"reason":"manual"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O001"))
                .andExpect(jsonPath("$.data.status").value("RELEASED"));
    }

    @Test
    void shouldAllowReserveWhenInternalTokenAndRoleValid() throws Exception {
        when(paymentService.reserveOrderPayment("O002", 1001L, 2001L, 100L))
                .thenReturn(new ReserveOrderPaymentResponse("O002", "P002", "RESERVED", false));

        HttpHeaders headers = signedHeaders("POST", "/api/payment/internal/orders/O002/reserve", null, "1001", INTERNAL_ROLE);
        headers.set(InternalPaymentOrderController.HEADER_INTERNAL_TOKEN, INTERNAL_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/payment/internal/orders/O002/reserve")
                        .headers(headers)
                        .content("""
                                {"buyerUserId":1001,"sellerUserId":2001,"amountCent":100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O002"))
                .andExpect(jsonPath("$.data.status").value("RESERVED"));
    }

    @Test
    void shouldAllowSettleWhenInternalTokenAndRoleValid() throws Exception {
        when(paymentService.settleOrderPayment("O003", "BUYER", 1001L))
                .thenReturn(new SettleOrderPaymentResponse("O003", "P003", "SETTLED", false));

        HttpHeaders headers = signedHeaders("POST", "/api/payment/internal/orders/O003/settle", null, "1001", INTERNAL_ROLE);
        headers.set(InternalPaymentOrderController.HEADER_INTERNAL_TOKEN, INTERNAL_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/payment/internal/orders/O003/settle")
                        .headers(headers)
                        .content("""
                                {"operatorType":"BUYER","operatorUserId":1001}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O003"))
                .andExpect(jsonPath("$.data.status").value("SETTLED"));
    }

    @Test
    void shouldAllowRefundWhenInternalTokenAndRoleValid() throws Exception {
        when(paymentService.refundOrderPayment("O004", "R004", "SYSTEM", 9001L, "manual"))
                .thenReturn(new RefundOrderPaymentResponse("O004", "R004", "P004", "SUCCEEDED", false));

        HttpHeaders headers = signedHeaders("POST", "/api/payment/internal/orders/O004/refund", null, "1001", INTERNAL_ROLE);
        headers.set(InternalPaymentOrderController.HEADER_INTERNAL_TOKEN, INTERNAL_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/payment/internal/orders/O004/refund")
                        .headers(headers)
                        .content("""
                                {"refundNo":"R004","operatorType":"SYSTEM","operatorUserId":9001,"reason":"manual"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O004"))
                .andExpect(jsonPath("$.data.refundStatus").value("SUCCEEDED"));
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
