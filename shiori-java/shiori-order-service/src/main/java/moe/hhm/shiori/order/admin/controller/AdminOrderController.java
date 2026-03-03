package moe.hhm.shiori.order.admin.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import moe.hhm.shiori.order.dto.CancelOrderRequest;
import moe.hhm.shiori.order.dto.OrderDetailResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.dto.OrderPageResponse;
import moe.hhm.shiori.order.security.CurrentUserSupport;
import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderCommandService orderCommandService;

    public AdminOrderController(OrderService orderService, OrderCommandService orderCommandService) {
        this.orderService = orderService;
        this.orderCommandService = orderCommandService;
    }

    @GetMapping
    public OrderPageResponse listOrders(@RequestParam(defaultValue = "1") @Min(1) int page,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                        @RequestParam(required = false) String orderNo,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Long buyerUserId,
                                        @RequestParam(required = false) Long sellerUserId) {
        return orderService.listOrdersForAdmin(orderNo, status, buyerUserId, sellerUserId, page, size);
    }

    @GetMapping("/{orderNo}")
    public OrderDetailResponse getOrderDetail(@PathVariable String orderNo) {
        return orderService.getOrderDetailForAdmin(orderNo);
    }

    @PostMapping("/{orderNo}/cancel")
    public OrderOperateResponse cancelOrder(@PathVariable String orderNo,
                                            @RequestBody(required = false) CancelOrderRequest request,
                                            Authentication authentication) {
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return orderCommandService.cancelOrderAsAdmin(
                operatorUserId,
                CurrentUserSupport.resolveRoles(authentication),
                orderNo,
                reason
        );
    }
}
