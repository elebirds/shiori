package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutSchedulerServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderCommandService orderCommandService;

    @Test
    void shouldDispatchExpiredUnpaidOrdersInBatch() {
        OrderProperties orderProperties = new OrderProperties();
        orderProperties.getTimeoutScheduler().setBatchSize(2);
        OrderTimeoutSchedulerService schedulerService =
                new OrderTimeoutSchedulerService(orderMapper, orderCommandService, orderProperties);
        when(orderMapper.listExpiredUnpaidOrderNos(any(LocalDateTime.class), eq(2)))
                .thenReturn(List.of("O202603260201", "O202603260202"));

        schedulerService.scanExpiredOrders();

        verify(orderCommandService).handleTimeout("O202603260201");
        verify(orderCommandService).handleTimeout("O202603260202");
    }

    @Test
    void shouldContinueWhenSingleTimeoutHandlingFails() {
        OrderProperties orderProperties = new OrderProperties();
        orderProperties.getTimeoutScheduler().setBatchSize(2);
        OrderTimeoutSchedulerService schedulerService =
                new OrderTimeoutSchedulerService(orderMapper, orderCommandService, orderProperties);
        when(orderMapper.listExpiredUnpaidOrderNos(any(LocalDateTime.class), eq(2)))
                .thenReturn(List.of("O202603260203", "O202603260204"));
        doThrow(new IllegalStateException("mock timeout failure"))
                .when(orderCommandService).handleTimeout("O202603260203");

        schedulerService.scanExpiredOrders();

        verify(orderCommandService).handleTimeout("O202603260203");
        verify(orderCommandService).handleTimeout("O202603260204");
    }
}
