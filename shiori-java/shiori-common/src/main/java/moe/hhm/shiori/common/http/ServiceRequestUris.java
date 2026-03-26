package moe.hhm.shiori.common.http;

import java.net.URI;

public final class ServiceRequestUris {

    private ServiceRequestUris() {
    }

    public static URI resolve(String serviceBaseUrl, String path) {
        if (serviceBaseUrl == null || serviceBaseUrl.isBlank()) {
            throw new IllegalArgumentException("serviceBaseUrl 不能为空");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }

        String normalizedBaseUrl = serviceBaseUrl.endsWith("/")
                ? serviceBaseUrl.substring(0, serviceBaseUrl.length() - 1)
                : serviceBaseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBaseUrl + normalizedPath);
    }
}
