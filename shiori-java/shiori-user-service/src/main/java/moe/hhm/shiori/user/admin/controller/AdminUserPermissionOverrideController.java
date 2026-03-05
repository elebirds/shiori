package moe.hhm.shiori.user.admin.controller;

import jakarta.validation.Valid;
import java.util.List;
import moe.hhm.shiori.user.authz.dto.AdminUserPermissionOverrideResponse;
import moe.hhm.shiori.user.authz.dto.AdminUserPermissionOverrideUpsertRequest;
import moe.hhm.shiori.user.authz.repository.UserAuthzMapper;
import moe.hhm.shiori.user.authz.service.UserPermissionOverrideService;
import moe.hhm.shiori.user.security.CurrentUserSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@ConditionalOnBean(UserAuthzMapper.class)
@RequestMapping("/api/v2/admin/users")
public class AdminUserPermissionOverrideController {

    private final UserPermissionOverrideService userPermissionOverrideService;

    public AdminUserPermissionOverrideController(UserPermissionOverrideService userPermissionOverrideService) {
        this.userPermissionOverrideService = userPermissionOverrideService;
    }

    @GetMapping("/{userId}/permission-overrides")
    public List<AdminUserPermissionOverrideResponse> listOverrides(@PathVariable Long userId) {
        return userPermissionOverrideService.listOverrides(userId);
    }

    @PostMapping("/{userId}/permission-overrides")
    public AdminUserPermissionOverrideResponse createOverride(@PathVariable Long userId,
                                                              @Valid @RequestBody AdminUserPermissionOverrideUpsertRequest request,
                                                              Authentication authentication) {
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        return userPermissionOverrideService.createOverride(operatorUserId, userId, request);
    }

    @PutMapping("/{userId}/permission-overrides/{overrideId}")
    public AdminUserPermissionOverrideResponse updateOverride(@PathVariable Long userId,
                                                              @PathVariable Long overrideId,
                                                              @Valid @RequestBody AdminUserPermissionOverrideUpsertRequest request,
                                                              Authentication authentication) {
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        return userPermissionOverrideService.updateOverride(operatorUserId, userId, overrideId, request);
    }

    @DeleteMapping("/{userId}/permission-overrides/{overrideId}")
    public void deleteOverride(@PathVariable Long userId,
                               @PathVariable Long overrideId,
                               @RequestParam(required = false) String reason,
                               Authentication authentication) {
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        userPermissionOverrideService.deleteOverride(operatorUserId, userId, overrideId, reason);
    }
}
