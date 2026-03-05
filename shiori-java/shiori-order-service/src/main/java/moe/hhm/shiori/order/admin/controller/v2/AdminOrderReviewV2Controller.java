package moe.hhm.shiori.order.admin.controller.v2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.order.dto.v2.AdminOrderReviewPageResponse;
import moe.hhm.shiori.order.dto.v2.AdminOrderReviewVisibilityRequest;
import moe.hhm.shiori.order.dto.v2.AdminOrderReviewVisibilityResponse;
import moe.hhm.shiori.order.security.CurrentUserSupport;
import moe.hhm.shiori.order.service.OrderReviewService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Validated
@RestController
@RequestMapping("/api/v2/admin/orders/reviews")
public class AdminOrderReviewV2Controller {

    private final OrderReviewService orderReviewService;
    private final PermissionGuard permissionGuard;

    public AdminOrderReviewV2Controller(OrderReviewService orderReviewService,
                                        PermissionGuard permissionGuard) {
        this.orderReviewService = orderReviewService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public AdminOrderReviewPageResponse listReviews(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                    @RequestParam(required = false) Long reviewedUserId,
                                                    @RequestParam(required = false) Long reviewerUserId,
                                                    @RequestParam(required = false) String reviewerRole,
                                                    @RequestParam(required = false) String visibilityStatus,
                                                    @RequestParam(required = false) BigDecimal minOverallStar,
                                                    @RequestParam(required = false) BigDecimal maxOverallStar,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                    LocalDateTime createdFrom,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                    LocalDateTime createdTo,
                                                    HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.review.moderate", httpServletRequest::getHeader);
        return orderReviewService.listAdminReviews(
                reviewedUserId,
                reviewerUserId,
                reviewerRole,
                visibilityStatus,
                minOverallStar,
                maxOverallStar,
                createdFrom,
                createdTo,
                page,
                size
        );
    }

    @PostMapping("/{reviewId}/visibility")
    public AdminOrderReviewVisibilityResponse updateVisibility(@PathVariable Long reviewId,
                                                               @Valid @RequestBody AdminOrderReviewVisibilityRequest request,
                                                               Authentication authentication,
                                                               HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.review.moderate", httpServletRequest::getHeader);
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        return orderReviewService.updateReviewVisibility(operatorUserId, reviewId, request.visible(), request.reason());
    }
}

