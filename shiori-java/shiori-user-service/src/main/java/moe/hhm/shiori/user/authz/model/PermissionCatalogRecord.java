package moe.hhm.shiori.user.authz.model;

public record PermissionCatalogRecord(
        String permissionCode,
        String domain,
        String action,
        String displayName,
        String description,
        boolean deprecated
) {
}
