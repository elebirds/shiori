package moe.hhm.shiori.common.security.authz;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public class PermissionGuard {

    public void require(String permissionCode, Function<String, String> headerResolver) {
        if (!isAllowed(permissionCode, headerResolver)) {
            throw new BizException(CommonErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "permission=" + normalize(permissionCode));
        }
    }

    public boolean isAllowed(String permissionCode, Function<String, String> headerResolver) {
        if (headerResolver == null || !StringUtils.hasText(permissionCode)) {
            return false;
        }
        String grantsHeader = headerResolver.apply(AuthzHeaderNames.USER_AUTHZ_GRANTS);
        String deniesHeader = headerResolver.apply(AuthzHeaderNames.USER_AUTHZ_DENIES);
        if (!StringUtils.hasText(grantsHeader) && !StringUtils.hasText(deniesHeader)) {
            // 服务侧二次校验在头缺失时走保可用策略，主判定由 gateway 完成。
            return true;
        }
        String normalizedPermission = normalize(permissionCode);
        Set<String> denies = parseHeaderSet(deniesHeader);
        if (denies.contains(normalizedPermission)) {
            return false;
        }
        Set<String> grants = parseHeaderSet(grantsHeader);
        return grants.contains(normalizedPermission);
    }

    public Set<String> parseHeaderSet(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(headerValue.split(","))
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .forEach(values::add);
        return values;
    }

    private String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace(':', '.');
    }
}
