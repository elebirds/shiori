package moe.hhm.shiori.product.domain;

public enum PostSourceType {
    MANUAL,
    AUTO_PRODUCT;

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
