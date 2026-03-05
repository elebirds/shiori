package moe.hhm.shiori.order.admin.controller.v2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.order.dto.CancelOrderRequest;
import moe.hhm.shiori.order.dto.OrderDetailResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.dto.OrderPageResponse;
import moe.hhm.shiori.order.dto.OrderStatusAuditPageResponse;
import moe.hhm.shiori.order.dto.OrderTransitionRequest;
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
@RequestMapping("/api/v2/admin/orders")
public class AdminOrderV2Controller {

    private final OrderService orderService;
    private final OrderCommandService orderCommandService;
    private final PermissionGuard permissionGuard;

    public AdminOrderV2Controller(OrderService orderService,
                                  OrderCommandService orderCommandService,
                                  PermissionGuard permissionGuard) {
        this.orderService = orderService;
        this.orderCommandService = orderCommandService;
        this.permissionGuard = permissionGuard;
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
                                            Authentication authentication,
                                            HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.cancel", httpServletRequest::getHeader);
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return orderCommandService.cancelOrderAsAdmin(
                operatorUserId,
                CurrentUserSupport.resolveRoles(authentication),
                orderNo,
                reason
        );
    }

    @PostMapping("/{orderNo}/deliver")
    public OrderOperateResponse deliverOrder(@PathVariable String orderNo,
                                             @Valid @RequestBody(required = false) OrderTransitionRequest request,
                                             Authentication authentication,
                                             HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.deliver", httpServletRequest::getHeader);
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return orderCommandService.deliverOrderAsAdmin(operatorUserId, orderNo, reason);
    }

    @PostMapping("/{orderNo}/finish")
    public OrderOperateResponse finishOrder(@PathVariable String orderNo,
                                            @Valid @RequestBody(required = false) OrderTransitionRequest request,
                                            Authentication authentication,
                                            HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.finish", httpServletRequest::getHeader);
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return orderCommandService.finishOrderAsAdmin(operatorUserId, orderNo, reason);
    }

    @GetMapping("/{orderNo}/status-audits")
    public OrderStatusAuditPageResponse listStatusAudits(@PathVariable String orderNo,
                                                         @RequestParam(defaultValue = "1") @Min(1) int page,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.listStatusAuditsForAdmin(orderNo, page, size);
    }
}
