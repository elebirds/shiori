package moe.hhm.shiori.order.domain;

import org.springframework.util.StringUtils;

public enum OrderCommandState {
    PREPARED,
    REMOTE_SUCCEEDED,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED;

    public static OrderCommandState fromCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        try {
            return OrderCommandState.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
