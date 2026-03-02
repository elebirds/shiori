package moe.hhm.shiori.common.error;

public enum GatewayErrorCode implements ErrorCode {
    ROUTE_NOT_FOUND(20000, "网关路由不存在"),
    INVALID_REQUEST(20001, "网关请求参数错误"),
    AUTH_REQUIRED(20002, "网关认证失败"),
    ACCESS_DENIED(20003, "网关无权限访问"),
    RATE_LIMITED(20004, "网关请求被限流"),
    UPSTREAM_UNAVAILABLE(20005, "下游服务不可用"),
    INTERNAL_ERROR(29999, "网关内部错误");

    private final int code;
    private final String message;

    GatewayErrorCode(int code, String message) {
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
