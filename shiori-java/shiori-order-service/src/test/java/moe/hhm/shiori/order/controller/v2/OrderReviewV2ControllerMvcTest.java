package moe.hhm.shiori.order.controller.v2;

import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.common.security.GatewaySignVerifyFilter;
import moe.hhm.shiori.common.security.authz.AuthzHeaderNames;
import moe.hhm.shiori.order.dto.v2.OrderReviewItemResponse;
import moe.hhm.shiori.order.dto.v2.UserReviewItemResponse;
import moe.hhm.shiori.order.dto.v2.UserReviewPageResponse;
import moe.hhm.shiori.order.service.OrderCartService;
import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderConfirmSettlementWorkflowService;
import moe.hhm.shiori.order.service.OrderCreateWorkflowService;
import moe.hhm.shiori.order.service.OrderPayWorkflowService;
import moe.hhm.shiori.order.service.OrderRefundService;
import moe.hhm.shiori.order.service.OrderReviewService;
import moe.hhm.shiori.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class OrderReviewV2ControllerMvcTest {

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
    void shouldCreateReviewWhenPermissionGranted() throws Exception {
        when(orderReviewService.createReview(eq(1001L), eq("O001"), any()))
                .thenReturn(new OrderReviewItemResponse(
                        1L,
                        "O001",
                        1001L,
                        2001L,
                        "BUYER",
                        5,
                        5,
                        5,
                        BigDecimal.valueOf(5.0),
                        "很好",
                        List.of("product/test/review-1.jpg"),
                        "VISIBLE",
                        null,
                        null,
                        null,
                        0,
                        null,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                ));

        HttpHeaders headers = signedHeaders(
                "POST",
                "/api/v2/order/orders/O001/reviews",
                null,
                "1001",
                "ROLE_USER",
                "order.review.create",
                null
        );
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/v2/order/orders/O001/reviews")
                        .headers(headers)
                        .content("""
                                {
                                  "communicationStar": 5,
                                  "timelinessStar": 5,
                                  "credibilityStar": 5,
                                  "comment": "很好"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewId").value(1))
                .andExpect(jsonPath("$.data.reviewerRole").value("BUYER"));
    }

    @Test
    void shouldReturn403WhenCreateReviewDeniedByPermissionHeader() throws Exception {
        HttpHeaders headers = signedHeaders(
                "POST",
                "/api/v2/order/orders/O001/reviews",
                null,
                "1001",
                "ROLE_USER",
                null,
                "order.review.create"
        );
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/v2/order/orders/O001/reviews")
                        .headers(headers)
                        .content("""
                                {
                                  "communicationStar": 5,
                                  "timelinessStar": 5,
                                  "credibilityStar": 5
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10004));
    }

    @Test
    void shouldReturn400WhenCreateReviewPayloadInvalid() throws Exception {
        HttpHeaders headers = signedHeaders(
                "POST",
                "/api/v2/order/orders/O001/reviews",
                null,
                "1001",
                "ROLE_USER",
                "order.review.create",
                null
        );
        headers.setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(post("/api/v2/order/orders/O001/reviews")
                        .headers(headers)
                        .content("""
                                {
                                  "communicationStar": 6,
                                  "timelinessStar": 5,
                                  "credibilityStar": 5
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void shouldListUserReviews() throws Exception {
        when(orderReviewService.listUserReviews(2001L, 1, 20))
                .thenReturn(new UserReviewPageResponse(
                        1,
                        1,
                        20,
                        List.of(new UserReviewItemResponse(
                                12L,
                                "O001",
                                1001L,
                                "BUYER",
                                5,
                                4,
                                5,
                                BigDecimal.valueOf(4.7),
                                "好评",
                                List.of("product/test/review-1.jpg"),
                                LocalDateTime.now()
                        ))
                ));

        HttpHeaders headers = signedHeaders("GET", "/api/v2/order/reviews/users/2001/reviews", null, "1001", "ROLE_USER", null, null);

        mockMvc.perform(get("/api/v2/order/reviews/users/2001/reviews").headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].reviewId").value(12))
                .andExpect(jsonPath("$.data.items[0].reviewerRole").value("BUYER"));
    }

    private HttpHeaders signedHeaders(String method,
                                      String path,
                                      String rawQuery,
                                      String userId,
                                      String roles,
                                      String authzGrants,
                                      String authzDenies) {
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-" + System.nanoTime();
        String canonical = GatewaySignUtils.buildCanonicalString(
                method,
                path,
                rawQuery,
                userId,
                roles,
                "",
                authzGrants,
                authzDenies,
                ts,
                nonce
        );
        String sign = GatewaySignUtils.hmacSha256Hex(SECRET, canonical);

        HttpHeaders headers = new HttpHeaders();
        headers.set(GatewaySignVerifyFilter.HEADER_USER_ID, userId);
        headers.set(GatewaySignVerifyFilter.HEADER_USER_ROLES, roles);
        if (authzGrants != null && !authzGrants.isBlank()) {
            headers.set(AuthzHeaderNames.USER_AUTHZ_GRANTS, authzGrants);
        }
        if (authzDenies != null && !authzDenies.isBlank()) {
            headers.set(AuthzHeaderNames.USER_AUTHZ_DENIES, authzDenies);
        }
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_TS, ts);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_NONCE, nonce);
        headers.set(GatewaySignVerifyFilter.HEADER_GATEWAY_SIGN, sign);
        return headers;
    }
}
