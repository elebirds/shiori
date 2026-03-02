package moe.hhm.shiori.common.error;

public enum CommonErrorCode implements ErrorCode {
    INVALID_PARAM(10000, "请求参数错误"),
    BAD_REQUEST(10001, "请求格式错误"),
    METHOD_NOT_ALLOWED(10002, "请求方法不支持"),
    UNAUTHORIZED(10003, "未认证或认证已过期"),
    FORBIDDEN(10004, "无权限访问"),
    NOT_FOUND(10005, "资源不存在"),
    TOO_MANY_REQUESTS(10006, "请求过于频繁"),
    SERVICE_UNAVAILABLE(10007, "服务暂不可用"),
    INTERNAL_SERVER_ERROR(19999, "系统内部错误");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
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
