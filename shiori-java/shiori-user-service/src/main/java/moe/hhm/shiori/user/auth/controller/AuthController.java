package moe.hhm.shiori.user.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import moe.hhm.shiori.user.auth.dto.ChangePasswordRequest;
import moe.hhm.shiori.user.auth.dto.LoginRequest;
import moe.hhm.shiori.user.auth.dto.LogoutRequest;
import moe.hhm.shiori.user.auth.dto.RegisterRequest;
import moe.hhm.shiori.user.auth.dto.RegisterResponse;
import moe.hhm.shiori.user.auth.dto.RefreshRequest;
import moe.hhm.shiori.user.auth.dto.TokenPairResponse;
import moe.hhm.shiori.user.auth.service.AuthService;
import moe.hhm.shiori.user.security.CurrentUserSupport;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.username(), request.password(), request.nickname());
    }

    @PostMapping("/login")
    public TokenPairResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request.username(), request.password(), resolveClientIp(servletRequest));
    }

    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return Map.of("success", true);
    }

    @PostMapping("/change-password")
    public Map<String, Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        authService.changePassword(userId, request.oldPassword(), request.newPassword());
        return Map.of("success", true);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
