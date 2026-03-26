package moe.hhm.shiori.order;

import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderConfirmSettlementWorkflowService;
import moe.hhm.shiori.order.service.OrderCreateWorkflowService;
import moe.hhm.shiori.order.service.OrderCartService;
import moe.hhm.shiori.order.service.OrderPayWorkflowService;
import moe.hhm.shiori.order.service.OrderReviewService;
import moe.hhm.shiori.order.service.OrderRefundService;
import moe.hhm.shiori.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "security.gateway-sign.internal-secret=test-gateway-sign-secret-32-bytes-0001",
        "order.payment-client.internal-token=test-order-payment-internal-token-000000000001",
        "order.command.enabled=false",
        "order.timeout-scheduler.enabled=false"
})
class ShioriOrderServiceApplicationTests {

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
    void contextLoads() {
    }
}
