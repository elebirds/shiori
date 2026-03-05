package moe.hhm.shiori.user.admin.model;

import java.util.Locale;
import java.util.Set;

public enum UserCapability {
    CHAT_SEND,
    CHAT_READ,
    PRODUCT_PUBLISH,
    ORDER_CREATE;

    public static UserCapability parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("capability is blank");
        }
        return UserCapability.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    public static Set<String> allCodes() {
        return Set.of(
                CHAT_SEND.name(),
                CHAT_READ.name(),
                PRODUCT_PUBLISH.name(),
                ORDER_CREATE.name()
        );
    }
}
