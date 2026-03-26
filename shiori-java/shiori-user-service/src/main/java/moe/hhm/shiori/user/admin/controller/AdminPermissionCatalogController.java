package moe.hhm.shiori.user.admin.controller;

import java.util.List;
import moe.hhm.shiori.user.authz.dto.AdminPermissionCatalogItemResponse;
import moe.hhm.shiori.user.authz.service.AdminPermissionCatalogService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2/admin/permissions")
public class AdminPermissionCatalogController {

    private final AdminPermissionCatalogService adminPermissionCatalogService;

    public AdminPermissionCatalogController(AdminPermissionCatalogService adminPermissionCatalogService) {
        this.adminPermissionCatalogService = adminPermissionCatalogService;
    }

    @GetMapping("/catalog")
    public List<AdminPermissionCatalogItemResponse> listCatalog() {
        return adminPermissionCatalogService.listCatalog();
    }
}
