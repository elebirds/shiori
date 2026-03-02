package moe.hhm.shiori.user.profile.controller;

import jakarta.validation.Valid;
import moe.hhm.shiori.user.profile.dto.UpdateProfileRequest;
import moe.hhm.shiori.user.profile.dto.UserProfileResponse;
import moe.hhm.shiori.user.profile.service.UserProfileService;
import moe.hhm.shiori.user.security.CurrentUserSupport;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public UserProfileResponse getMyProfile(Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return userProfileService.getMyProfile(userId);
    }

    @PutMapping("/me")
    public UserProfileResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request,
                                               Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return userProfileService.updateMyProfile(userId, request);
    }
}
