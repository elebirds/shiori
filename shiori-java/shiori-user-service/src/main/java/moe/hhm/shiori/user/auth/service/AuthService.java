package moe.hhm.shiori.user.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.auth.dto.TokenPairResponse;
import moe.hhm.shiori.user.auth.model.UserAuthRecord;
import moe.hhm.shiori.user.auth.repository.AuthUserMapper;
import moe.hhm.shiori.user.domain.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthUserMapper authUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(AuthUserMapper authUserMapper, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.authUserMapper = authUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public TokenPairResponse login(String username, String password, String loginIp) {
        UserAuthRecord user = authUserMapper.findByUsername(username);
        if (user == null || isDeleted(user)) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED);
        }

        UserStatus status = resolveStatus(user.status());
        if (status == UserStatus.DISABLED) {
            throw new BizException(UserErrorCode.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN);
        }
        if (status == UserStatus.LOCKED && isStillLocked(user.lockedUntil())) {
            throw new BizException(UserErrorCode.ACCOUNT_LOCKED, HttpStatus.FORBIDDEN);
        }

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            authUserMapper.increaseFailedLoginCount(user.id());
            throw new BizException(UserErrorCode.PASSWORD_INCORRECT, HttpStatus.UNAUTHORIZED);
        }

        authUserMapper.markLoginSuccess(user.id(), loginIp);
        List<String> roles = authUserMapper.findRolesByUserId(user.id());
        return tokenService.issueTokenPair(user.id(), user.userNo(), user.username(), roles);
    }

    public TokenPairResponse refresh(String refreshToken) {
        return tokenService.refresh(refreshToken);
    }

    public void logout(String refreshToken) {
        tokenService.logout(refreshToken);
    }

    private boolean isDeleted(UserAuthRecord user) {
        return user.isDeleted() != null && user.isDeleted() == 1;
    }

    private boolean isStillLocked(LocalDateTime lockedUntil) {
        return lockedUntil == null || lockedUntil.isAfter(LocalDateTime.now());
    }

    private UserStatus resolveStatus(Integer status) {
        if (status == null) {
            return UserStatus.ENABLED;
        }
        for (UserStatus value : UserStatus.values()) {
            if (value.getCode() == status) {
                return value;
            }
        }
        return UserStatus.ENABLED;
    }
}
