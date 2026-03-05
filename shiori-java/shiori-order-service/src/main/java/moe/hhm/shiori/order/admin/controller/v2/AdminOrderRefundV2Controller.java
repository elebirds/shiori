package moe.hhm.shiori.order.admin.controller.v2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.order.dto.OrderRefundPageResponse;
import moe.hhm.shiori.order.dto.OrderRefundResponse;
import moe.hhm.shiori.order.security.CurrentUserSupport;
import moe.hhm.shiori.order.service.OrderRefundService;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2/admin/orders/refunds")
public class AdminOrderRefundV2Controller {

    private final OrderRefundService orderRefundService;
    private final PermissionGuard permissionGuard;

    public AdminOrderRefundV2Controller(OrderRefundService orderRefundService,
                                        PermissionGuard permissionGuard) {
        this.orderRefundService = orderRefundService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public OrderRefundPageResponse list(@RequestParam(required = false) String refundNo,
                                        @RequestParam(required = false) String orderNo,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Long buyerUserId,
                                        @RequestParam(required = false) Long sellerUserId,
                                        @RequestParam(defaultValue = "1") @Min(1) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
                                        HttpServletRequest request) {
        permissionGuard.require("order.refund.manage", request::getHeader);
        return orderRefundService.listRefundsForAdmin(refundNo, orderNo, status, buyerUserId, sellerUserId, page, size);
    }

    @PostMapping("/{refundNo}/retry")
    public OrderRefundResponse retry(@PathVariable String refundNo,
                                     Authentication authentication,
                                     HttpServletRequest request) {
        permissionGuard.require("order.refund.manage", request::getHeader);
        Long adminUserId = CurrentUserSupport.requireUserId(authentication);
        return orderRefundService.retryRefundAsAdmin(adminUserId, refundNo);
    }
}
