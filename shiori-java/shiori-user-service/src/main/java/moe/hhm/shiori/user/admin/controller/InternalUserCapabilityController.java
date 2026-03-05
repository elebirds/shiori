package moe.hhm.shiori.user.admin.controller;

import java.util.List;
import moe.hhm.shiori.common.mvc.SkipResultWrap;
import moe.hhm.shiori.user.admin.dto.ActiveCapabilityListResponse;
import moe.hhm.shiori.user.admin.service.AdminUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SkipResultWrap
@RestController
@RequestMapping("/internal/users")
public class InternalUserCapabilityController {

    private final AdminUserService adminUserService;

    public InternalUserCapabilityController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/{userId}/capabilities/active")
    public ActiveCapabilityListResponse listActiveCapabilities(@PathVariable Long userId) {
        List<String> capabilities = adminUserService.listActiveCapabilities(userId);
        return new ActiveCapabilityListResponse(userId, capabilities);
    }
}
