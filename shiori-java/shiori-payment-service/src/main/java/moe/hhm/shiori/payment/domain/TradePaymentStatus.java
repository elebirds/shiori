package moe.hhm.shiori.payment.domain;

import moe.hhm.shiori.common.error.PaymentErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import org.springframework.http.HttpStatus;

public enum TradePaymentStatus {
    RESERVED(1),
    SETTLED(2),
    RELEASED(3);

    private final int code;

    TradePaymentStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static TradePaymentStatus fromCode(Integer code) {
        if (code == null) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        for (TradePaymentStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
    }
}
