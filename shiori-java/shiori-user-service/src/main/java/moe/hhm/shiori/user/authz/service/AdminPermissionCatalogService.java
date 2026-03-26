package moe.hhm.shiori.user.authz.service;

import java.util.List;
import moe.hhm.shiori.user.authz.dto.AdminPermissionCatalogItemResponse;
import moe.hhm.shiori.user.authz.model.PermissionCatalogRecord;
import moe.hhm.shiori.user.authz.repository.UserAuthzMapper;
import org.springframework.stereotype.Service;

@Service
public class AdminPermissionCatalogService {

    private final UserAuthzMapper userAuthzMapper;

    public AdminPermissionCatalogService(UserAuthzMapper userAuthzMapper) {
        this.userAuthzMapper = userAuthzMapper;
    }

    public List<AdminPermissionCatalogItemResponse> listCatalog() {
        return userAuthzMapper.listPermissionCatalog().stream()
                .map(this::toResponse)
                .toList();
    }

    private AdminPermissionCatalogItemResponse toResponse(PermissionCatalogRecord record) {
        return new AdminPermissionCatalogItemResponse(
                record.permissionCode(),
                record.domain(),
                record.action(),
                record.displayName(),
                record.description(),
                record.deprecated()
        );
    }
}
