package moe.hhm.shiori.order.domain;

public enum OrderStatus {
    UNPAID(1),
    PAID(2),
    CANCELED(3),
    DELIVERING(4),
    FINISHED(5),
    REFUNDED(6);

    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static OrderStatus fromCode(Integer code) {
        if (code == null) {
            return UNPAID;
        }
        for (OrderStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNPAID;
    }
}
