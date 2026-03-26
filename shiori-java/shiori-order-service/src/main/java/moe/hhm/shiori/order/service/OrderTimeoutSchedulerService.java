package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "order.timeout-scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutSchedulerService.class);

    private final OrderMapper orderMapper;
    private final OrderCommandService orderCommandService;
    private final OrderProperties orderProperties;

    public OrderTimeoutSchedulerService(OrderMapper orderMapper,
                                        OrderCommandService orderCommandService,
                                        OrderProperties orderProperties) {
        this.orderMapper = orderMapper;
        this.orderCommandService = orderCommandService;
        this.orderProperties = orderProperties;
    }

    @Scheduled(fixedDelayString = "${order.timeout-scheduler.fixed-delay-ms:3000}")
    public void scanExpiredOrders() {
        int batchSize = Math.max(orderProperties.getTimeoutScheduler().getBatchSize(), 1);
        List<String> orderNos = orderMapper.listExpiredUnpaidOrderNos(LocalDateTime.now(), batchSize);
        for (String orderNo : orderNos) {
            try {
                orderCommandService.handleTimeout(orderNo);
            } catch (RuntimeException ex) {
                log.warn("处理超时订单失败, orderNo={}, err={}", orderNo, ex.getMessage());
            }
        }
    }
}
