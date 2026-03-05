package moe.hhm.shiori.payment.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.payment.dto.admin.CreateCdkBatchRequest;
import moe.hhm.shiori.payment.dto.admin.CreateCdkBatchResponse;
import moe.hhm.shiori.payment.security.CurrentUserSupport;
import moe.hhm.shiori.payment.service.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v2/admin/payments/cdks")
public class AdminPaymentCdkController {

    private final PaymentService paymentService;
    private final PermissionGuard permissionGuard;

    public AdminPaymentCdkController(PaymentService paymentService,
                                     PermissionGuard permissionGuard) {
        this.paymentService = paymentService;
        this.permissionGuard = permissionGuard;
    }

    @PostMapping("/batches")
    public CreateCdkBatchResponse createBatch(@Valid @RequestBody CreateCdkBatchRequest request,
                                              Authentication authentication,
                                              HttpServletRequest httpServletRequest) {
        permissionGuard.require("payment.cdk.manage", httpServletRequest::getHeader);
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        return paymentService.createCdkBatch(operatorUserId, request);
    }
}
