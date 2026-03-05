package moe.hhm.shiori.order.domain;

public enum OrderRefundStatus {
    REQUESTED,
    REJECTED,
    PENDING_FUNDS,
    SUCCEEDED;

    public static OrderRefundStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderRefundStatus value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return null;
    }
}
