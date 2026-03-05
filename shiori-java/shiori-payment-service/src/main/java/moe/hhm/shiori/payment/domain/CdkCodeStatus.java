package moe.hhm.shiori.payment.domain;

public enum CdkCodeStatus {
    UNUSED(1),
    USED(2);

    private final int code;

    CdkCodeStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
