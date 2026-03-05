package moe.hhm.shiori.payment.domain;

import moe.hhm.shiori.common.error.PaymentErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import org.springframework.http.HttpStatus;

public enum TradeRefundStatus {
    PENDING_FUNDS(1),
    SUCCEEDED(2);

    private final int code;

    TradeRefundStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static TradeRefundStatus fromCode(Integer code) {
        if (code == null) {
            throw new BizException(PaymentErrorCode.PAYMENT_REFUND_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        for (TradeRefundStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new BizException(PaymentErrorCode.PAYMENT_REFUND_STATUS_INVALID, HttpStatus.CONFLICT);
    }
}
