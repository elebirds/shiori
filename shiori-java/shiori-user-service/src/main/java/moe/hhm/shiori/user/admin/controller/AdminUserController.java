package moe.hhm.shiori.user.admin.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import moe.hhm.shiori.user.admin.dto.AdminUserAdminRoleUpdateRequest;
import moe.hhm.shiori.user.admin.dto.AdminUserDetailResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserPageResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserStatusResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserStatusUpdateRequest;
import moe.hhm.shiori.user.admin.service.AdminUserService;
import moe.hhm.shiori.user.security.CurrentUserSupport;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public AdminUserPageResponse listUsers(@RequestParam(defaultValue = "1") @Min(1) int page,
                                           @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String role) {
        return adminUserService.listUsers(keyword, status, role, page, size);
    }

    @GetMapping("/{userId}")
    public AdminUserDetailResponse getUserDetail(@PathVariable Long userId) {
        return adminUserService.getUserDetail(userId);
    }

    @PostMapping("/{userId}/status")
    public AdminUserStatusResponse updateUserStatus(@PathVariable Long userId,
                                                    @Valid @RequestBody AdminUserStatusUpdateRequest request,
                                                    Authentication authentication) {
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        return adminUserService.updateUserStatus(operatorUserId, userId, request);
    }

    @PutMapping("/{userId}/admin-role")
    public AdminUserStatusResponse updateAdminRole(@PathVariable Long userId,
                                                   @Valid @RequestBody AdminUserAdminRoleUpdateRequest request,
                                                   Authentication authentication) {
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        return adminUserService.updateAdminRole(operatorUserId, userId, request);
    }
}
