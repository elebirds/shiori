package moe.hhm.shiori.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderRefundAutoApproveJob {

    private final OrderRefundService orderRefundService;

    public OrderRefundAutoApproveJob(OrderRefundService orderRefundService) {
        this.orderRefundService = orderRefundService;
    }

    @Scheduled(fixedDelayString = "${order.refund.auto-approve-fixed-delay-ms:60000}")
    public void autoApprove() {
        orderRefundService.autoApproveExpiredRefunds();
    }
}
