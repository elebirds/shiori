package moe.hhm.shiori.payment.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.payment.config.InternalApiProperties;
import moe.hhm.shiori.payment.dto.internal.RefundOrderPaymentRequest;
import moe.hhm.shiori.payment.dto.internal.RefundOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.ReleaseOrderPaymentRequest;
import moe.hhm.shiori.payment.dto.internal.ReleaseOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.ReserveOrderPaymentRequest;
import moe.hhm.shiori.payment.dto.internal.ReserveOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.SettleOrderPaymentRequest;
import moe.hhm.shiori.payment.dto.internal.SettleOrderPaymentResponse;
import moe.hhm.shiori.payment.security.CurrentUserSupport;
import moe.hhm.shiori.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment/internal/orders")
public class InternalPaymentOrderController {

    public static final String HEADER_INTERNAL_TOKEN = "X-Shiori-Internal-Token";
    public static final String INTERNAL_CALLER_ROLE = "ROLE_INTERNAL_ORDER_SERVICE";

    private final PaymentService paymentService;
    private final InternalApiProperties internalApiProperties;

    public InternalPaymentOrderController(PaymentService paymentService,
                                          InternalApiProperties internalApiProperties) {
        this.paymentService = paymentService;
        this.internalApiProperties = internalApiProperties;
    }

    @PostMapping("/{orderNo}/reserve")
    public ReserveOrderPaymentResponse reserve(@PathVariable String orderNo,
                                               @Valid @RequestBody ReserveOrderPaymentRequest request,
                                               Authentication authentication,
                                               HttpServletRequest httpServletRequest) {
        validateInternalToken(httpServletRequest);
        validateInternalCaller(authentication);
        CurrentUserSupport.requireUserId(authentication);
        return paymentService.reserveOrderPayment(orderNo, request.buyerUserId(), request.sellerUserId(), request.amountCent());
    }

    @PostMapping("/{orderNo}/settle")
    public SettleOrderPaymentResponse settle(@PathVariable String orderNo,
                                             @Valid @RequestBody SettleOrderPaymentRequest request,
                                             Authentication authentication,
                                             HttpServletRequest httpServletRequest) {
        validateInternalToken(httpServletRequest);
        validateInternalCaller(authentication);
        CurrentUserSupport.requireUserId(authentication);
        return paymentService.settleOrderPayment(orderNo, request.operatorType(), request.operatorUserId());
    }

    @PostMapping("/{orderNo}/release")
    public ReleaseOrderPaymentResponse release(@PathVariable String orderNo,
                                               @Valid @RequestBody(required = false) ReleaseOrderPaymentRequest request,
                                               Authentication authentication,
                                               HttpServletRequest httpServletRequest) {
        validateInternalToken(httpServletRequest);
        validateInternalCaller(authentication);
        CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return paymentService.releaseOrderPayment(orderNo, reason);
    }

    @PostMapping("/{orderNo}/refund")
    public RefundOrderPaymentResponse refund(@PathVariable String orderNo,
                                             @Valid @RequestBody RefundOrderPaymentRequest request,
                                             Authentication authentication,
                                             HttpServletRequest httpServletRequest) {
        validateInternalToken(httpServletRequest);
        validateInternalCaller(authentication);
        CurrentUserSupport.requireUserId(authentication);
        return paymentService.refundOrderPayment(orderNo, request.refundNo(), request.operatorType(),
                request.operatorUserId(), request.reason());
    }

    private void validateInternalToken(HttpServletRequest request) {
        if (!internalApiProperties.isRequireToken()) {
            return;
        }
        String expected = internalApiProperties.getToken();
        String actual = request.getHeader(HEADER_INTERNAL_TOKEN);
        if (!StringUtils.hasText(actual)
                || !StringUtils.hasText(expected)
                || !GatewaySignUtils.constantTimeEquals(expected.trim(), actual.trim())) {
            throw new BizException(CommonErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "内部调用令牌校验失败");
        }
    }

    private void validateInternalCaller(Authentication authentication) {
        if (CurrentUserSupport.resolveRoles(authentication).stream()
                .noneMatch(INTERNAL_CALLER_ROLE::equals)) {
            throw new BizException(CommonErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "内部调用角色校验失败");
        }
    }
}
