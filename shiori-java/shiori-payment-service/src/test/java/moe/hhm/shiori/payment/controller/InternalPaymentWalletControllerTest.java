package moe.hhm.shiori.payment.controller;

import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.payment.dto.internal.InitWalletAccountResponse;
import moe.hhm.shiori.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
        "payment.reconcile.enabled=false"
})
@AutoConfigureMockMvc
class InternalPaymentWalletControllerTest {

    private static final String SECRET = "test-gateway-sign-secret-32-bytes-0001";
    private static final String INTERNAL_TOKEN = "test-order-payment-internal-token-000000000001";
    private static final String INTERNAL_ROLE = "ROLE_INTERNAL_USER_SERVICE";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void shouldAllowInitWalletWhenInternalTokenAndRoleValid() throws Exception {
        when(paymentService.initWalletAccount(1001L))
                .thenReturn(new InitWalletAccountResponse(1001L, "READY", false));

        HttpHeaders headers = signedHeaders("POST", "/api/payment/internal/wallets/1001/init", null, "1001", INTERNAL_ROLE);
        headers.set(InternalPaymentOrderController.HEADER_INTERNAL_TOKEN, INTERNAL_TOKEN);

        mockMvc.perform(post("/api/payment/internal/wallets/1001/init")
                        .headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1001))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.idempotent").value(false));
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
