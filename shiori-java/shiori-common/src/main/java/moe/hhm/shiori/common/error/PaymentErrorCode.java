package moe.hhm.shiori.common.error;

public enum PaymentErrorCode implements ErrorCode {
    PAYMENT_BALANCE_NOT_ENOUGH(60000, "余额不足"),
    PAYMENT_BALANCE_INVALID(60001, "余额账户状态非法"),
    PAYMENT_TRADE_NOT_FOUND(60002, "支付交易不存在"),
    PAYMENT_TRADE_STATUS_INVALID(60003, "支付交易状态非法"),
    PAYMENT_CDK_INVALID(60004, "CDK无效"),
    PAYMENT_CDK_ALREADY_REDEEMED(60005, "CDK已兑换"),
    PAYMENT_CDK_EXPIRED(60006, "CDK已过期"),
    PAYMENT_REFUND_NOT_FOUND(60007, "退款记录不存在"),
    PAYMENT_REFUND_STATUS_INVALID(60008, "退款状态非法"),
    PAYMENT_REFUND_PENDING_FUNDS(60009, "退款挂起，卖家可用余额不足");

    private final int code;
    private final String message;

    PaymentErrorCode(int code, String message) {
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
