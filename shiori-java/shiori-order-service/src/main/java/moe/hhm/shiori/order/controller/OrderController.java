package moe.hhm.shiori.order.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.servlet.http.HttpServletRequest;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.order.dto.CancelOrderRequest;
import moe.hhm.shiori.order.dto.CreateOrderRequest;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
import moe.hhm.shiori.order.dto.OrderDetailResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.dto.OrderPageResponse;
import moe.hhm.shiori.order.dto.PayOrderRequest;
import moe.hhm.shiori.order.security.CurrentUserSupport;
import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/order/orders")
public class OrderController {

    private final OrderCommandService orderCommandService;
    private final OrderService orderService;
    private final PermissionGuard permissionGuard;

    public OrderController(OrderCommandService orderCommandService,
                           OrderService orderService,
                           PermissionGuard permissionGuard) {
        this.orderCommandService = orderCommandService;
        this.orderService = orderService;
        this.permissionGuard = permissionGuard;
    }

    @PostMapping
    public CreateOrderResponse create(@Valid @RequestBody CreateOrderRequest request,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                      Authentication authentication,
                                      HttpServletRequest httpServletRequest) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        permissionGuard.require("order.create", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderCommandService.createOrder(
                userId,
                CurrentUserSupport.resolveRoles(authentication),
                idempotencyKey.trim(),
                request
        );
    }

    @GetMapping("/{orderNo}")
    public OrderDetailResponse detail(@PathVariable String orderNo, Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderService.getOrderDetail(userId, CurrentUserSupport.hasRoleAdmin(authentication), orderNo);
    }

    @GetMapping
    public OrderPageResponse list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                  @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                  Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderService.listMyOrders(userId, page, size);
    }

    @PostMapping("/{orderNo}/pay")
    public OrderOperateResponse pay(@PathVariable String orderNo,
                                    @Valid @RequestBody PayOrderRequest request,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    Authentication authentication,
                                    HttpServletRequest httpServletRequest) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        permissionGuard.require("order.pay", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderCommandService.payOrder(userId, orderNo, request.paymentNo().trim(), idempotencyKey.trim());
    }

    @PostMapping("/{orderNo}/cancel")
    public OrderOperateResponse cancel(@PathVariable String orderNo,
                                       @Valid @RequestBody(required = false) CancelOrderRequest request,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                       Authentication authentication,
                                       HttpServletRequest httpServletRequest) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        permissionGuard.require("order.cancel", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return orderCommandService.cancelOrder(
                userId,
                CurrentUserSupport.resolveRoles(authentication),
                orderNo,
                reason,
                idempotencyKey.trim()
        );
    }
}
