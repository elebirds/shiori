package moe.hhm.shiori.payment.controller;

import jakarta.servlet.http.HttpServletRequest;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.payment.dto.WalletBalanceResponse;
import moe.hhm.shiori.payment.security.CurrentUserSupport;
import moe.hhm.shiori.payment.service.PaymentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/payment/wallet")
public class PaymentWalletController {

    private final PaymentService paymentService;
    private final PermissionGuard permissionGuard;

    public PaymentWalletController(PaymentService paymentService,
                                   PermissionGuard permissionGuard) {
        this.paymentService = paymentService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/balance")
    public WalletBalanceResponse balance(Authentication authentication,
                                         HttpServletRequest request) {
        permissionGuard.require("payment.wallet.read", request::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return paymentService.getWalletBalance(userId);
    }
}
