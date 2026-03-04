package moe.hhm.shiori.product.domain;

import java.util.Locale;
import java.util.Set;

public enum ProductTradeMode {
    MEETUP,
    DELIVERY,
    BOTH;

    private static final Set<String> CODES = Set.of(
            MEETUP.name(),
            DELIVERY.name(),
            BOTH.name()
    );

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!CODES.contains(normalized)) {
            return null;
        }
        return normalized;
    }
}
