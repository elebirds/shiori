package moe.hhm.shiori.common.security.authz;

public final class AuthzHeaderNames {

    public static final String USER_AUTHZ_VERSION = "X-User-Authz-Version";
    public static final String USER_AUTHZ_GRANTS = "X-User-Authz-Grants";
    public static final String USER_AUTHZ_DENIES = "X-User-Authz-Denies";

    private AuthzHeaderNames() {
    }
}
