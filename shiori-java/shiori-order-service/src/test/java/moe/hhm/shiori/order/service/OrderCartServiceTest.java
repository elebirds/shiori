package moe.hhm.shiori.order.service;

import java.util.List;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.client.ProductDetailSnapshot;
import moe.hhm.shiori.order.client.ProductServiceClient;
import moe.hhm.shiori.order.client.ProductSkuSnapshot;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
import moe.hhm.shiori.order.dto.v2.CartAddItemRequest;
import moe.hhm.shiori.order.dto.v2.CartCheckoutRequest;
import moe.hhm.shiori.order.model.CartItemRecord;
import moe.hhm.shiori.order.model.CartRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCartServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private OrderCommandService orderCommandService;

    private OrderCartService orderCartService;

    @BeforeEach
    void setUp() {
        orderCartService = new OrderCartService(orderMapper, productServiceClient, orderCommandService);
    }

    @Test
    void shouldRejectCrossSellerWhenAddItem() {
        when(orderMapper.findCartByBuyerId(1001L)).thenReturn(new CartRecord(1L, 1001L, 3001L));
        when(productServiceClient.getProductDetail(1L, 1001L, List.of("ROLE_USER")))
                .thenReturn(new ProductDetailSnapshot(
                        1L,
                        "P001",
                        2001L,
                        "书籍",
                        "描述",
                        "ON_SALE",
                        List.of(new ProductSkuSnapshot(11L, "S11", "规格:标准", "{}", 1200L, 10))
                ));

        assertThatThrownBy(() -> orderCartService.addItem(
                1001L,
                List.of("ROLE_USER"),
                new CartAddItemRequest(1L, 11L, 1)
        ))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50024);
    }

    @Test
    void shouldCheckoutSelectedItems() {
        when(orderMapper.findCartByBuyerId(1001L))
                .thenReturn(new CartRecord(1L, 1001L, 2001L))
                .thenReturn(new CartRecord(1L, 1001L, 2001L));
        when(orderMapper.listCartItemsByBuyerId(1001L)).thenReturn(List.of(
                new CartItemRecord(10L, 1L, 1001L, 2001L, 1L, 11L, 2),
                new CartItemRecord(11L, 1L, 1001L, 2001L, 1L, 12L, 1)
        ));
        when(orderCommandService.createOrder(eq(1001L), eq(List.of("ROLE_USER")), any(), any()))
                .thenReturn(new CreateOrderResponse("O202603060001", "UNPAID", 2400L, 2, false));
        when(orderMapper.countCartItemsByCartId(1L)).thenReturn(0L);

        CreateOrderResponse response = orderCartService.checkout(
                1001L,
                List.of("ROLE_USER"),
                "idem-cart-1",
                new CartCheckoutRequest(List.of(10L), null, null)
        );

        assertThat(response.orderNo()).isEqualTo("O202603060001");
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderMapper).deleteCartItemsByIdsAndBuyer(eq(1001L), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(10L);
        verify(orderMapper).deleteCartByBuyerWhenEmpty(1001L);
    }
}
