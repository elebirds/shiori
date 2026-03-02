package moe.hhm.shiori.common.error;

public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND(50000, "订单不存在"),
    ORDER_EMPTY_ITEMS(50001, "订单项不能为空"),
    ORDER_ITEM_INVALID(50002, "订单项非法"),
    ORDER_CROSS_SELLER_NOT_ALLOWED(50003, "暂不支持跨卖家合单"),
    ORDER_STATUS_INVALID(50004, "订单状态非法"),
    ORDER_STOCK_NOT_ENOUGH(50005, "库存不足"),
    ORDER_NO_PERMISSION(50006, "无订单操作权限"),
    ORDER_DUPLICATE_REQUEST(50007, "重复下单请求"),
    ORDER_PAYMENT_CONFLICT(50008, "支付流水号冲突"),
    ORDER_PRODUCT_INVALID(50009, "商品信息非法或不可下单");

    private final int code;
    private final String message;

    OrderErrorCode(int code, String message) {
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
