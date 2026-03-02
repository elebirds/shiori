package moe.hhm.shiori.user.domain;

public enum UserStatus {
    ENABLED(1),
    DISABLED(2),
    LOCKED(3);

    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
