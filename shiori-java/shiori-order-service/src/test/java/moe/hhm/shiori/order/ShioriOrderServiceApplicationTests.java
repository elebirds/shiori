package moe.hhm.shiori.order;

import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "security.gateway-sign.internal-secret=test-gateway-sign-secret-32-bytes-0001",
        "order.payment-client.internal-token=test-order-payment-internal-token-000000000001"
})
class ShioriOrderServiceApplicationTests {

    @MockitoBean
    private OrderCommandService orderCommandService;

    @MockitoBean
    private OrderService orderService;

    @Test
    void contextLoads() {
    }
}
