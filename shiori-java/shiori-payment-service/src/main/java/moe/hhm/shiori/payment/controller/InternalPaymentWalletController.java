package moe.hhm.shiori.payment.controller;

import jakarta.servlet.http.HttpServletRequest;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.security.GatewaySignUtils;
import moe.hhm.shiori.payment.config.InternalApiProperties;
import moe.hhm.shiori.payment.dto.internal.InitWalletAccountResponse;
import moe.hhm.shiori.payment.security.CurrentUserSupport;
import moe.hhm.shiori.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment/internal/wallets")
public class InternalPaymentWalletController {

    static final String INTERNAL_CALLER_ROLE = "ROLE_INTERNAL_USER_SERVICE";

    private final PaymentService paymentService;
    private final InternalApiProperties internalApiProperties;

    public InternalPaymentWalletController(PaymentService paymentService,
                                           InternalApiProperties internalApiProperties) {
        this.paymentService = paymentService;
        this.internalApiProperties = internalApiProperties;
    }

    @PostMapping("/{userId}/init")
    public InitWalletAccountResponse init(@PathVariable Long userId,
                                          Authentication authentication,
                                          HttpServletRequest httpServletRequest) {
        validateInternalToken(httpServletRequest);
        validateInternalCaller(authentication);
        CurrentUserSupport.requireUserId(authentication);
        return paymentService.initWalletAccount(userId);
    }

    private void validateInternalToken(HttpServletRequest request) {
        if (!internalApiProperties.isRequireToken()) {
            return;
        }
        String expected = internalApiProperties.getToken();
        String actual = request.getHeader(InternalPaymentOrderController.HEADER_INTERNAL_TOKEN);
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
