package moe.hhm.shiori.gateway.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

public final class JwtClaimUtils {

    private JwtClaimUtils() {
    }

    public static String resolveUserId(Jwt jwt) {
        if (StringUtils.hasText(jwt.getSubject())) {
            return jwt.getSubject();
        }

        String uid = jwt.getClaimAsString("uid");
        if (StringUtils.hasText(uid)) {
            return uid;
        }

        return null;
    }

    public static List<String> normalizeRoles(Object rolesClaim) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String role : extractRawRoles(rolesClaim)) {
            String cleaned = role.trim();
            if (!StringUtils.hasText(cleaned)) {
                continue;
            }
            String upper = cleaned.toUpperCase(Locale.ROOT);
            String withPrefix = upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
            normalized.add(withPrefix);
        }
        return List.copyOf(normalized);
    }

    private static List<String> extractRawRoles(Object rolesClaim) {
        if (rolesClaim == null) {
            return List.of();
        }

        if (rolesClaim instanceof String str) {
            if (!StringUtils.hasText(str)) {
                return List.of();
            }
            String[] segments = str.split(",");
            List<String> result = new ArrayList<>(segments.length);
            for (String segment : segments) {
                if (StringUtils.hasText(segment)) {
                    result.add(segment.trim());
                }
            }
            return result;
        }

        if (rolesClaim instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }

        return List.of(rolesClaim.toString());
    }
}
