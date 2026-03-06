package moe.hhm.shiori.order.controller.v2;

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
import moe.hhm.shiori.order.dto.CreateOrderRefundRequest;
import moe.hhm.shiori.order.dto.OrderDetailResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.dto.OrderPageResponse;
import moe.hhm.shiori.order.dto.OrderRefundResponse;
import moe.hhm.shiori.order.dto.v2.ChatToOrderClickRequest;
import moe.hhm.shiori.order.dto.v2.ConfirmReceiptRequest;
import moe.hhm.shiori.order.dto.v2.OrderOperateResponseV2;
import moe.hhm.shiori.order.dto.v2.OrderTimelineResponse;
import moe.hhm.shiori.order.dto.v2.UpdateOrderFulfillmentRequest;
import moe.hhm.shiori.order.security.CurrentUserSupport;
import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderRefundService;
import moe.hhm.shiori.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2/order/orders")
public class OrderV2Controller {

    private final OrderCommandService orderCommandService;
    private final OrderRefundService orderRefundService;
    private final OrderService orderService;
    private final PermissionGuard permissionGuard;

    public OrderV2Controller(OrderCommandService orderCommandService,
                             OrderRefundService orderRefundService,
                             OrderService orderService,
                             PermissionGuard permissionGuard) {
        this.orderCommandService = orderCommandService;
        this.orderRefundService = orderRefundService;
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

    @PostMapping("/chat-to-order-click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordChatToOrderClick(@Valid @RequestBody ChatToOrderClickRequest request,
                                       Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        orderCommandService.recordChatToOrderClick(userId, request);
    }

    @PostMapping("/{orderNo}/pay")
    public OrderOperateResponse pay(@PathVariable String orderNo,
                                    @RequestBody(required = false) String rawBody,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    Authentication authentication,
                                    HttpServletRequest httpServletRequest) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        if (StringUtils.hasText(rawBody)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST, "v2支付接口不接受请求体");
        }
        permissionGuard.require("order.pay", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderCommandService.payOrderByBalance(userId, orderNo, idempotencyKey.trim());
    }

    @PutMapping("/{orderNo}/fulfillment")
    public OrderDetailResponse updateFulfillment(@PathVariable String orderNo,
                                                 @Valid @RequestBody UpdateOrderFulfillmentRequest request,
                                                 Authentication authentication,
                                                 HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.pay", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        orderCommandService.updateOrderFulfillmentByBuyer(
                userId,
                CurrentUserSupport.resolveRoles(authentication),
                orderNo,
                request
        );
        return orderService.getOrderDetail(userId, false, orderNo);
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

    @PostMapping("/{orderNo}/confirm-receipt")
    public OrderOperateResponseV2 confirmReceipt(@PathVariable String orderNo,
                                                 @Valid @RequestBody(required = false) ConfirmReceiptRequest request,
                                                 Authentication authentication,
                                                 HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.confirm_receipt", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        OrderOperateResponse response = orderCommandService.confirmReceiptAsBuyer(userId, orderNo, reason);
        return new OrderOperateResponseV2(response.orderNo(), response.status(), response.idempotent());
    }

    @GetMapping("/{orderNo}/timeline")
    public OrderTimelineResponse timeline(@PathVariable String orderNo,
                                          @RequestParam(defaultValue = "1") @Min(1) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                          Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderService.listOrderTimeline(userId, CurrentUserSupport.hasRoleAdmin(authentication), orderNo, page, size);
    }

    @PostMapping("/{orderNo}/refunds")
    public OrderRefundResponse applyRefund(@PathVariable String orderNo,
                                           @Valid @RequestBody CreateOrderRefundRequest request,
                                           Authentication authentication,
                                           HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.refund.apply", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderRefundService.applyRefund(userId, orderNo, request.reason());
    }

    @GetMapping("/{orderNo}/refunds/latest")
    public OrderRefundResponse latestRefund(@PathVariable String orderNo,
                                            Authentication authentication,
                                            HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.refund.apply", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderRefundService.getLatestRefundForBuyer(userId, orderNo);
    }
}
