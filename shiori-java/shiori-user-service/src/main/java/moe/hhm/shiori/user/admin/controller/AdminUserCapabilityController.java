package moe.hhm.shiori.user.admin.controller;

import jakarta.validation.Valid;
import java.util.List;
import moe.hhm.shiori.user.admin.dto.AdminUserCapabilityBanResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserCapabilityBanUpsertRequest;
import moe.hhm.shiori.user.admin.service.AdminUserService;
import moe.hhm.shiori.user.security.CurrentUserSupport;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2/admin/users")
public class AdminUserCapabilityController {

    private final AdminUserService adminUserService;

    public AdminUserCapabilityController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/{userId}/capability-bans")
    public List<AdminUserCapabilityBanResponse> listCapabilityBans(@PathVariable Long userId) {
        return adminUserService.listCapabilityBans(userId);
    }

    @PostMapping("/{userId}/capability-bans")
    public AdminUserCapabilityBanResponse upsertCapabilityBan(@PathVariable Long userId,
                                                              @Valid @RequestBody AdminUserCapabilityBanUpsertRequest request,
                                                              Authentication authentication) {
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        return adminUserService.upsertCapabilityBan(operatorUserId, userId, request);
    }

    @DeleteMapping("/{userId}/capability-bans/{capability}")
    public AdminUserCapabilityBanResponse removeCapabilityBan(@PathVariable Long userId,
                                                              @PathVariable String capability,
                                                              @RequestParam(required = false) String reason,
                                                              Authentication authentication) {
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        return adminUserService.removeCapabilityBan(operatorUserId, userId, capability, reason);
    }
}
