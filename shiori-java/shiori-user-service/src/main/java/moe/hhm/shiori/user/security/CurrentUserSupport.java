package moe.hhm.shiori.user.security;

import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;

public final class CurrentUserSupport {

    private CurrentUserSupport() {
    }

    public static Long requireUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
        String principal = authentication.getName();
        if (!StringUtils.hasText(principal)) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
    }
}
