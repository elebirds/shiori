package moe.hhm.shiori.order.controller.v2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.order.dto.OrderRefundPageResponse;
import moe.hhm.shiori.order.dto.OrderRefundResponse;
import moe.hhm.shiori.order.dto.ReviewOrderRefundRequest;
import moe.hhm.shiori.order.security.CurrentUserSupport;
import moe.hhm.shiori.order.service.OrderRefundService;
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
@RequestMapping("/api/v2/order/seller/refunds")
public class SellerRefundV2Controller {

    private final OrderRefundService orderRefundService;
    private final PermissionGuard permissionGuard;

    public SellerRefundV2Controller(OrderRefundService orderRefundService,
                                    PermissionGuard permissionGuard) {
        this.orderRefundService = orderRefundService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public OrderRefundPageResponse list(@RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "1") @Min(1) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                        Authentication authentication,
                                        HttpServletRequest request) {
        permissionGuard.require("order.refund.review", request::getHeader);
        Long sellerUserId = CurrentUserSupport.requireUserId(authentication);
        return orderRefundService.listRefundsForSeller(sellerUserId, status, page, size);
    }

    @PostMapping("/{refundNo}/approve")
    public OrderRefundResponse approve(@PathVariable String refundNo,
                                       @Valid @RequestBody(required = false) ReviewOrderRefundRequest request,
                                       Authentication authentication,
                                       HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.refund.review", httpServletRequest::getHeader);
        Long sellerUserId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return orderRefundService.approveRefundAsSeller(sellerUserId, refundNo, reason);
    }

    @PostMapping("/{refundNo}/reject")
    public OrderRefundResponse reject(@PathVariable String refundNo,
                                      @Valid @RequestBody(required = false) ReviewOrderRefundRequest request,
                                      Authentication authentication,
                                      HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.refund.review", httpServletRequest::getHeader);
        Long sellerUserId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return orderRefundService.rejectRefundAsSeller(sellerUserId, refundNo, reason);
    }
}
