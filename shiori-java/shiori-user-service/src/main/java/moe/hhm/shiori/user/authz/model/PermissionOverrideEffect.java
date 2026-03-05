package moe.hhm.shiori.user.authz.model;

import java.util.Locale;

public enum PermissionOverrideEffect {
    ALLOW,
    DENY;

    public static PermissionOverrideEffect parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("effect is blank");
        }
        return PermissionOverrideEffect.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
