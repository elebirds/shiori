package moe.hhm.shiori.common.exception;

import moe.hhm.shiori.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final Object extraData;

    public BizException(ErrorCode errorCode) {
        this(errorCode, HttpStatus.BAD_REQUEST, null);
    }

    public BizException(ErrorCode errorCode, HttpStatus httpStatus) {
        this(errorCode, httpStatus, null);
    }

    public BizException(ErrorCode errorCode, HttpStatus httpStatus, Object extraData) {
        super(errorCode.message());
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.extraData = extraData;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Object getExtraData() {
        return extraData;
    }
}
