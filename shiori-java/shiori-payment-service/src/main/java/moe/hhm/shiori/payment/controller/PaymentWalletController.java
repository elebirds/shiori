package moe.hhm.shiori.payment.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.payment.dto.WalletBalanceResponse;
import moe.hhm.shiori.payment.dto.WalletLedgerPageResponse;
import moe.hhm.shiori.payment.security.CurrentUserSupport;
import moe.hhm.shiori.payment.service.PaymentService;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Validated
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

    @GetMapping("/ledger")
    public WalletLedgerPageResponse listLedger(@RequestParam(required = false) String bizType,
                                               @RequestParam(required = false) String bizNo,
                                               @RequestParam(required = false) String changeType,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                               LocalDateTime createdFrom,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                               LocalDateTime createdTo,
                                               @RequestParam(defaultValue = "1") @Min(1) int page,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                               Authentication authentication,
                                               HttpServletRequest request) {
        permissionGuard.require("payment.wallet.ledger.read", request::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return paymentService.listWalletLedgerByUser(userId, bizType, bizNo, changeType, createdFrom, createdTo, page, size);
    }
}
