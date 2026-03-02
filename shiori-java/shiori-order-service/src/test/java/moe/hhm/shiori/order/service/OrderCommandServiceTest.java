package moe.hhm.shiori.order.service;

import java.util.List;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.client.ProductDetailSnapshot;
import moe.hhm.shiori.order.client.ProductServiceClient;
import moe.hhm.shiori.order.client.ProductSkuSnapshot;
import moe.hhm.shiori.order.config.OrderMqProperties;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.dto.CreateOrderItem;
import moe.hhm.shiori.order.dto.CreateOrderRequest;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ProductServiceClient productServiceClient;

    private OrderCommandService orderCommandService;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties();
        OrderMqProperties orderMqProperties = new OrderMqProperties();
        orderCommandService = new OrderCommandService(
                orderMapper,
                productServiceClient,
                orderProperties,
                orderMqProperties,
                new ObjectMapper()
        );
    }

    @Test
    void shouldRejectCrossSellerOrder() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new CreateOrderItem(1L, 11L, 1),
                new CreateOrderItem(2L, 22L, 1)
        ));
        when(orderMapper.findOrderNoByBuyerAndIdempotencyKey(1001L, "idem-1")).thenReturn(null);
        when(productServiceClient.getProductDetail(1L, 1001L, List.of("ROLE_USER")))
                .thenReturn(product(1L, "P1", 2001L, 11L, "S11", 1200L));
        when(productServiceClient.getProductDetail(2L, 1001L, List.of("ROLE_USER")))
                .thenReturn(product(2L, "P2", 2002L, 22L, "S22", 1300L));

        BizException ex;
        try {
            orderCommandService.createOrder(1001L, List.of("ROLE_USER"), "idem-1", request);
            throw new AssertionError("expected BizException");
        } catch (BizException actual) {
            ex = actual;
        }
        assertThat(ex.getErrorCode().code()).isEqualTo(50003);
        verify(productServiceClient, never()).deductStock(anyLong(), any(), anyString(), anyLong(), anyList());
    }

    @Test
    void shouldReturnIdempotentCreateResponseWhenOrderExists() {
        when(orderMapper.findOrderNoByBuyerAndIdempotencyKey(1001L, "idem-2")).thenReturn("O202603030001");
        when(orderMapper.findOrderByOrderNo("O202603030001"))
                .thenReturn(new OrderRecord(
                        1L, "O202603030001", 1001L, 2001L, 1, 999L, 1,
                        null, null, null, null, 0, null, null
                ));

        CreateOrderResponse response = orderCommandService.createOrder(
                1001L,
                List.of("ROLE_USER"),
                "idem-2",
                new CreateOrderRequest(List.of(new CreateOrderItem(1L, 11L, 1)))
        );

        assertThat(response.idempotent()).isTrue();
        assertThat(response.orderNo()).isEqualTo("O202603030001");
        verify(productServiceClient, never()).getProductDetail(anyLong(), any(), anyList());
    }

    @Test
    void shouldReturnIdempotentPayWhenAlreadyPaid() {
        when(orderMapper.findOrderByOrderNo("O202603030009"))
                .thenReturn(new OrderRecord(
                        9L, "O202603030009", 1001L, 2001L, 2, 1999L, 2,
                        "PAY-001", null, null, null, 0, null, null
                ));

        OrderOperateResponse response = orderCommandService.payOrder(1001L, "O202603030009", "PAY-001");
        assertThat(response.idempotent()).isTrue();
        assertThat(response.status()).isEqualTo("PAID");
        verify(orderMapper, never()).markOrderPaid(anyString(), anyLong(), anyString(), any(), any(), any());
    }

    private ProductDetailSnapshot product(Long productId, String productNo, Long ownerUserId,
                                          Long skuId, String skuNo, Long priceCent) {
        return new ProductDetailSnapshot(
                productId,
                productNo,
                ownerUserId,
                "title",
                "desc",
                "ON_SALE",
                List.of(new ProductSkuSnapshot(skuId, skuNo, "sku", "{}", priceCent, 100))
        );
    }
}
