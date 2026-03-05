package moe.hhm.shiori.user.authz.dto;

public record AdminPermissionCatalogItemResponse(
        String permissionCode,
        String domain,
        String action,
        String displayName,
        String description,
        boolean deprecated
) {
}
