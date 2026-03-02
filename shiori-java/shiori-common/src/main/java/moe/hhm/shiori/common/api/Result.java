package moe.hhm.shiori.common.api;

import moe.hhm.shiori.common.error.ErrorCode;

public record Result<T>(int code, String message, T data, long timestamp) {

    public static final int SUCCESS_CODE = 0;
    public static final String SUCCESS_MESSAGE = "成功";

    public static <T> Result<T> success() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null, System.currentTimeMillis());
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, System.currentTimeMillis());
    }

    public static <T> Result<T> failure(ErrorCode errorCode) {
        return new Result<>(errorCode.code(), errorCode.message(), null, System.currentTimeMillis());
    }

    public static <T> Result<T> failure(ErrorCode errorCode, T data) {
        return new Result<>(errorCode.code(), errorCode.message(), data, System.currentTimeMillis());
    }

    public static <T> Result<T> failure(int code, String message, T data) {
        return new Result<>(code, message, data, System.currentTimeMillis());
    }
}
