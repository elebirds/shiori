package moe.hhm.shiori.order.controller.v2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.order.dto.v2.OrderReviewContextResponse;
import moe.hhm.shiori.order.dto.v2.OrderReviewItemResponse;
import moe.hhm.shiori.order.dto.v2.OrderReviewUpsertRequest;
import moe.hhm.shiori.order.dto.v2.PraiseWallPageResponse;
import moe.hhm.shiori.order.dto.v2.UserCreditProfileResponse;
import moe.hhm.shiori.order.security.CurrentUserSupport;
import moe.hhm.shiori.order.service.OrderReviewService;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2/order")
public class OrderReviewV2Controller {

    private final OrderReviewService orderReviewService;
    private final PermissionGuard permissionGuard;

    public OrderReviewV2Controller(OrderReviewService orderReviewService,
                                   PermissionGuard permissionGuard) {
        this.orderReviewService = orderReviewService;
        this.permissionGuard = permissionGuard;
    }

    @PostMapping("/orders/{orderNo}/reviews")
    public OrderReviewItemResponse createReview(@PathVariable String orderNo,
                                                @Valid @RequestBody OrderReviewUpsertRequest request,
                                                Authentication authentication,
                                                HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.review.create", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderReviewService.createReview(userId, orderNo, request);
    }

    @PutMapping("/orders/{orderNo}/reviews/me")
    public OrderReviewItemResponse updateMyReview(@PathVariable String orderNo,
                                                  @Valid @RequestBody OrderReviewUpsertRequest request,
                                                  Authentication authentication,
                                                  HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.review.update", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderReviewService.updateMyReview(userId, orderNo, request);
    }

    @GetMapping("/orders/{orderNo}/reviews")
    public OrderReviewContextResponse getOrderReviewContext(@PathVariable String orderNo,
                                                            Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return orderReviewService.getOrderReviewContext(userId, orderNo);
    }

    @GetMapping("/reviews/users/{userId}/credit-profile")
    public UserCreditProfileResponse getUserCreditProfile(@PathVariable Long userId) {
        return orderReviewService.getUserCreditProfile(userId);
    }

    @GetMapping("/reviews/users/{userId}/praise-wall")
    public PraiseWallPageResponse listPraiseWall(@PathVariable Long userId,
                                                 @RequestParam(defaultValue = "1") @Min(1) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderReviewService.listPraiseWall(userId, page, size);
    }
}

