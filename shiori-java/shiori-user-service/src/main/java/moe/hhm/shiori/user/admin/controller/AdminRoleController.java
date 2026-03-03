package moe.hhm.shiori.user.admin.controller;

import java.util.List;
import moe.hhm.shiori.user.admin.dto.AdminRoleResponse;
import moe.hhm.shiori.user.admin.service.AdminUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/roles")
public class AdminRoleController {

    private final AdminUserService adminUserService;

    public AdminRoleController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AdminRoleResponse> listRoles() {
        return adminUserService.listRoles();
    }
}
