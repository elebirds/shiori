package moe.hhm.shiori.user.profile.controller;

import jakarta.validation.Valid;
import moe.hhm.shiori.common.mvc.SkipResultWrap;
import moe.hhm.shiori.user.profile.dto.AvatarUploadResponse;
import moe.hhm.shiori.user.profile.dto.PublicUserProfileResponse;
import moe.hhm.shiori.user.profile.dto.UpdateProfileRequest;
import moe.hhm.shiori.user.profile.dto.UserProfileResponse;
import moe.hhm.shiori.user.profile.service.UserProfileService;
import moe.hhm.shiori.user.security.CurrentUserSupport;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

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

    @GetMapping("/profiles/{userNo}")
    public PublicUserProfileResponse getPublicProfile(@PathVariable String userNo,
                                                      Authentication authentication) {
        return userProfileService.getProfileByUserNo(userNo, resolveOptionalUserId(authentication));
    }

    @GetMapping("/profiles/by-user-ids")
    public List<PublicUserProfileResponse> getPublicProfilesByUserIds(@RequestParam List<Long> userIds) {
        return userProfileService.getProfilesByUserIds(userIds);
    }

    @PutMapping("/me")
    public UserProfileResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request,
                                               Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return userProfileService.updateMyProfile(userId, request);
    }

    @PostMapping("/me/active-ping")
    public void pingMyActive(Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        userProfileService.pingActive(userId);
    }

    @PostMapping(value = "/media/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarUploadResponse uploadMyAvatar(@RequestPart("file") MultipartFile file,
                                               Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return userProfileService.uploadMyAvatar(userId, file);
    }

    @SkipResultWrap
    @GetMapping("/media/avatar/{avatarKey}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String avatarKey) {
        var avatar = userProfileService.loadAvatar(avatarKey);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(avatar.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)))
                .contentType(mediaType)
                .body(avatar.bytes());
    }

    private Long resolveOptionalUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String principal = authentication.getName();
        if (principal == null || principal.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
