package moe.hhm.shiori.product.domain;

public enum ProductStatus {
    DRAFT(1),
    ON_SALE(2),
    OFF_SHELF(3);

    private final int code;

    ProductStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ProductStatus fromCode(Integer code) {
        if (code == null) {
            return DRAFT;
        }
        for (ProductStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return DRAFT;
    }
}
