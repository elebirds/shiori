package moe.hhm.shiori.order.domain;

public enum OrderPaymentMode {
    SIMULATED,
    BALANCE_ESCROW;

    public static OrderPaymentMode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return SIMULATED;
        }
        for (OrderPaymentMode value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return SIMULATED;
    }
}
