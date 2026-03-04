package moe.hhm.shiori.common.error;

public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(30000, "用户不存在"),
    USERNAME_ALREADY_EXISTS(30001, "用户名已存在"),
    PASSWORD_INCORRECT(30002, "用户名或密码错误"),
    ACCOUNT_DISABLED(30003, "账号已禁用"),
    ACCOUNT_LOCKED(30004, "账号已锁定"),
    ROLE_NOT_FOUND(30005, "角色不存在"),
    PERMISSION_DENIED(30006, "权限不足"),
    CANNOT_DISABLE_SELF(30007, "不能禁用当前登录账号"),
    CANNOT_REVOKE_SELF_ADMIN(30008, "不能回收当前登录账号的管理员角色"),
    ADMIN_MIN_ONE_REQUIRED(30009, "系统至少保留一名启用状态管理员"),
    CANNOT_LOCK_SELF(30010, "不能锁定当前登录账号"),
    USER_NOT_LOCKED(30011, "账号未锁定"),
    PASSWORD_RESET_INVALID(30012, "重置密码参数不合法");

    private final int code;
    private final String message;

    UserErrorCode(int code, String message) {
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
