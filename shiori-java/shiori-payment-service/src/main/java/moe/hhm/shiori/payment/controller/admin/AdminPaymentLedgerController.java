package moe.hhm.shiori.payment.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.payment.dto.WalletLedgerPageResponse;
import moe.hhm.shiori.payment.dto.admin.ReconcileIssuePageResponse;
import moe.hhm.shiori.payment.dto.admin.UpdateReconcileIssueStatusRequest;
import moe.hhm.shiori.payment.security.CurrentUserSupport;
import moe.hhm.shiori.payment.service.PaymentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2/admin/payments")
public class AdminPaymentLedgerController {

    private final PaymentService paymentService;
    private final PermissionGuard permissionGuard;

    public AdminPaymentLedgerController(PaymentService paymentService,
                                        PermissionGuard permissionGuard) {
        this.paymentService = paymentService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/wallet-ledgers")
    public WalletLedgerPageResponse listWalletLedgers(@RequestParam(required = false) Long userId,
                                                      @RequestParam(required = false) String bizType,
                                                      @RequestParam(required = false) String bizNo,
                                                      @RequestParam(required = false) String changeType,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                      LocalDateTime createdFrom,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                      LocalDateTime createdTo,
                                                      @RequestParam(defaultValue = "1") @Min(1) int page,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
                                                      HttpServletRequest request) {
        permissionGuard.require("payment.wallet.ledger.manage", request::getHeader);
        return paymentService.listWalletLedgerForAdmin(userId, bizType, bizNo, changeType, createdFrom, createdTo, page, size);
    }

    @GetMapping("/reconcile/issues")
    public ReconcileIssuePageResponse listReconcileIssues(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String issueType,
                                                          @RequestParam(defaultValue = "1") @Min(1) int page,
                                                          @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
                                                          HttpServletRequest request) {
        permissionGuard.require("payment.wallet.ledger.manage", request::getHeader);
        return paymentService.listReconcileIssues(status, issueType, page, size);
    }

    @PostMapping("/reconcile/issues/{issueNo}/status")
    public void updateReconcileIssueStatus(@PathVariable String issueNo,
                                           @Valid @RequestBody UpdateReconcileIssueStatusRequest body,
                                           Authentication authentication,
                                           HttpServletRequest request) {
        permissionGuard.require("payment.wallet.ledger.manage", request::getHeader);
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        paymentService.updateReconcileIssueStatus(issueNo, body.fromStatus(), body.toStatus(), operatorUserId);
    }
}
