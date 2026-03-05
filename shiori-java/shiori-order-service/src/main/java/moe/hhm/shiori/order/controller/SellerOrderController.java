package moe.hhm.shiori.order.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.dto.OrderTransitionRequest;
import moe.hhm.shiori.order.security.CurrentUserSupport;
import moe.hhm.shiori.order.service.OrderCommandService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order/seller/orders")
public class SellerOrderController {

    private final OrderCommandService orderCommandService;
    private final PermissionGuard permissionGuard;

    public SellerOrderController(OrderCommandService orderCommandService,
                                 PermissionGuard permissionGuard) {
        this.orderCommandService = orderCommandService;
        this.permissionGuard = permissionGuard;
    }

    @PostMapping("/{orderNo}/deliver")
    public OrderOperateResponse deliver(@PathVariable String orderNo,
                                        @Valid @RequestBody(required = false) OrderTransitionRequest request,
                                        Authentication authentication,
                                        HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.deliver", httpServletRequest::getHeader);
        Long sellerUserId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return orderCommandService.deliverOrderAsSeller(sellerUserId, orderNo, reason);
    }

    @PostMapping("/{orderNo}/finish")
    public OrderOperateResponse finish(@PathVariable String orderNo,
                                       @Valid @RequestBody(required = false) OrderTransitionRequest request,
                                       Authentication authentication,
                                       HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.finish", httpServletRequest::getHeader);
        Long sellerUserId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return orderCommandService.finishOrderAsSeller(sellerUserId, orderNo, reason);
    }
}
