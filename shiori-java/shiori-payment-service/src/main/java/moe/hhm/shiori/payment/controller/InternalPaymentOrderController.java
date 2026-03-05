package moe.hhm.shiori.payment.controller;

import jakarta.validation.Valid;
import moe.hhm.shiori.payment.dto.internal.ReleaseOrderPaymentRequest;
import moe.hhm.shiori.payment.dto.internal.ReleaseOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.ReserveOrderPaymentRequest;
import moe.hhm.shiori.payment.dto.internal.ReserveOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.SettleOrderPaymentRequest;
import moe.hhm.shiori.payment.dto.internal.SettleOrderPaymentResponse;
import moe.hhm.shiori.payment.security.CurrentUserSupport;
import moe.hhm.shiori.payment.service.PaymentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment/internal/orders")
public class InternalPaymentOrderController {

    private final PaymentService paymentService;

    public InternalPaymentOrderController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{orderNo}/reserve")
    public ReserveOrderPaymentResponse reserve(@PathVariable String orderNo,
                                               @Valid @RequestBody ReserveOrderPaymentRequest request,
                                               Authentication authentication) {
        CurrentUserSupport.requireUserId(authentication);
        return paymentService.reserveOrderPayment(orderNo, request.buyerUserId(), request.sellerUserId(), request.amountCent());
    }

    @PostMapping("/{orderNo}/settle")
    public SettleOrderPaymentResponse settle(@PathVariable String orderNo,
                                             @Valid @RequestBody SettleOrderPaymentRequest request,
                                             Authentication authentication) {
        CurrentUserSupport.requireUserId(authentication);
        return paymentService.settleOrderPayment(orderNo, request.operatorType(), request.operatorUserId());
    }

    @PostMapping("/{orderNo}/release")
    public ReleaseOrderPaymentResponse release(@PathVariable String orderNo,
                                               @Valid @RequestBody(required = false) ReleaseOrderPaymentRequest request,
                                               Authentication authentication) {
        CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return paymentService.releaseOrderPayment(orderNo, reason);
    }
}
