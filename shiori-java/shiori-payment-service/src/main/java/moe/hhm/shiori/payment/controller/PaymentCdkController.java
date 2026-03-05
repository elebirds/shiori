package moe.hhm.shiori.payment.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.payment.dto.RedeemCdkRequest;
import moe.hhm.shiori.payment.dto.RedeemCdkResponse;
import moe.hhm.shiori.payment.security.CurrentUserSupport;
import moe.hhm.shiori.payment.service.PaymentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/payment/cdks")
public class PaymentCdkController {

    private final PaymentService paymentService;
    private final PermissionGuard permissionGuard;

    public PaymentCdkController(PaymentService paymentService,
                                PermissionGuard permissionGuard) {
        this.paymentService = paymentService;
        this.permissionGuard = permissionGuard;
    }

    @PostMapping("/redeem")
    public RedeemCdkResponse redeem(@Valid @RequestBody RedeemCdkRequest request,
                                    Authentication authentication,
                                    HttpServletRequest httpServletRequest) {
        permissionGuard.require("payment.cdk.redeem", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return paymentService.redeemCdk(userId, request.code());
    }
}
