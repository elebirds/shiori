package moe.hhm.shiori.order.domain;

import org.springframework.util.StringUtils;

public enum OrderCommandType {
    CREATE_ORDER,
    PAY_BALANCE_ORDER;

    public static OrderCommandType fromCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        try {
            return OrderCommandType.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
