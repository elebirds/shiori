package moe.hhm.shiori.common.error;

public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(40000, "商品不存在"),
    PRODUCT_NOT_ON_SALE(40001, "商品未上架"),
    SKU_NOT_FOUND(40002, "SKU不存在"),
    STOCK_NOT_ENOUGH(40003, "库存不足"),
    NO_PRODUCT_PERMISSION(40004, "无商品操作权限"),
    INVALID_PRODUCT_STATUS(40005, "商品状态非法"),
    INVALID_MEDIA_OBJECT_KEY(40006, "商品图片对象键非法");

    private final int code;
    private final String message;

    ProductErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
