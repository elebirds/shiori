package moe.hhm.shiori.product.domain;

import java.util.Locale;
import java.util.Set;

public enum ProductCategoryCode {
    TEXTBOOK,
    EXAM_MATERIAL,
    NOTE,
    OTHER;

    private static final Set<String> CODES = Set.of(
            TEXTBOOK.name(),
            EXAM_MATERIAL.name(),
            NOTE.name(),
            OTHER.name()
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
