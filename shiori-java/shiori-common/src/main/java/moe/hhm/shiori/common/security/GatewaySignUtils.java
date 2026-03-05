package moe.hhm.shiori.common.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class GatewaySignUtils {

    private GatewaySignUtils() {
    }

    public static String buildCanonicalString(String method, String path, String rawQuery,
                                              String userId, String userRoles, String timestamp) {
        return buildCanonicalString(method, path, rawQuery, userId, userRoles, "", "", "", timestamp, "");
    }

    public static String buildCanonicalString(String method, String path, String rawQuery,
                                              String userId, String userRoles, String timestamp,
                                              String nonce) {
        return buildCanonicalString(method, path, rawQuery, userId, userRoles, "", "", "", timestamp, nonce);
    }

    public static String buildCanonicalString(String method, String path, String rawQuery,
                                              String userId, String userRoles,
                                              String authzVersion, String authzGrants, String authzDenies,
                                              String timestamp, String nonce) {
        String safeQuery = rawQuery == null ? "" : rawQuery;
        String safeUserId = userId == null ? "" : userId;
        String safeUserRoles = userRoles == null ? "" : userRoles;
        String safeAuthzVersion = authzVersion == null ? "" : authzVersion;
        String safeAuthzGrants = authzGrants == null ? "" : authzGrants;
        String safeAuthzDenies = authzDenies == null ? "" : authzDenies;
        return String.join("\n",
                method == null ? "" : method.toUpperCase(),
                path == null ? "" : path,
                safeQuery,
                safeUserId,
                safeUserRoles,
                safeAuthzVersion,
                safeAuthzGrants,
                safeAuthzDenies,
                timestamp == null ? "" : timestamp,
                nonce == null ? "" : nonce);
    }

    public static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("无法计算 HMAC 签名", e);
        }
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法计算 SHA-256", e);
        }
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
