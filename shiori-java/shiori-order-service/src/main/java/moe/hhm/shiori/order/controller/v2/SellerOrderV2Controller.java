package moe.hhm.shiori.order.controller.v2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.order.dto.OrderDetailResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.dto.OrderTransitionRequest;
import moe.hhm.shiori.order.dto.v2.SellerOrderPageResponse;
import moe.hhm.shiori.order.security.CurrentUserSupport;
import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@ConditionalOnProperty(prefix = "feature.api-v2", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v2/order/seller/orders")
public class SellerOrderV2Controller {

    private final OrderCommandService orderCommandService;
    private final OrderService orderService;
    private final PermissionGuard permissionGuard;

    public SellerOrderV2Controller(OrderCommandService orderCommandService,
                                   OrderService orderService,
                                   PermissionGuard permissionGuard) {
        this.orderCommandService = orderCommandService;
        this.orderService = orderService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public SellerOrderPageResponse list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                        @RequestParam(required = false) String orderNo,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                        LocalDateTime createdFrom,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                        LocalDateTime createdTo,
                                        Authentication authentication) {
        Long sellerUserId = CurrentUserSupport.requireUserId(authentication);
        return orderService.listSellerOrders(sellerUserId, orderNo, status, createdFrom, createdTo, page, size);
    }

    @GetMapping("/{orderNo}")
    public OrderDetailResponse detail(@PathVariable String orderNo, Authentication authentication) {
        Long sellerUserId = CurrentUserSupport.requireUserId(authentication);
        return orderService.getOrderDetailForSeller(sellerUserId, orderNo);
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
