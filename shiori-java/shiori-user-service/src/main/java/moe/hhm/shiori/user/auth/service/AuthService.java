package moe.hhm.shiori.user.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.auth.config.UserSecurityProperties;
import moe.hhm.shiori.user.auth.dto.RegisterResponse;
import moe.hhm.shiori.user.auth.model.RegisterUserEntity;
import moe.hhm.shiori.user.auth.dto.TokenPairResponse;
import moe.hhm.shiori.user.auth.model.UserAuthRecord;
import moe.hhm.shiori.user.auth.repository.AuthUserMapper;
import moe.hhm.shiori.user.domain.UserStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final AuthUserMapper authUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserSecurityProperties userSecurityProperties;

    public AuthService(AuthUserMapper authUserMapper,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       UserSecurityProperties userSecurityProperties) {
        this.authUserMapper = authUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userSecurityProperties = userSecurityProperties;
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
        if (status == UserStatus.LOCKED) {
            authUserMapper.unlockUser(user.id());
            user = authUserMapper.findById(user.id());
            if (user == null || isDeleted(user)) {
                throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED);
            }
        }

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            int lockThreshold = Math.max(userSecurityProperties.getLoginFailLockThreshold(), 1);
            long lockMinutes = Math.max(userSecurityProperties.getLockMinutes(), 1);
            authUserMapper.recordLoginFailure(user.id(), lockThreshold, LocalDateTime.now().plusMinutes(lockMinutes));
            throw new BizException(UserErrorCode.PASSWORD_INCORRECT, HttpStatus.UNAUTHORIZED);
        }

        authUserMapper.markLoginSuccess(user.id(), loginIp);
        List<String> roles = authUserMapper.findRolesByUserId(user.id());
        boolean mustChangePassword = user.mustChangePassword() != null && user.mustChangePassword() == 1;
        return tokenService.issueTokenPair(user.id(), user.userNo(), user.username(), roles, mustChangePassword);
    }

    public TokenPairResponse refresh(String refreshToken) {
        return tokenService.refresh(refreshToken);
    }

    @Transactional(rollbackFor = Exception.class)
    public RegisterResponse register(String username, String password, String nickname) {
        if (authUserMapper.countByUsername(username) > 0) {
            throw new BizException(UserErrorCode.USERNAME_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }

        Long roleId = authUserMapper.findRoleIdByCode("ROLE_USER");
        if (roleId == null) {
            throw new BizException(UserErrorCode.ROLE_NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        RegisterUserEntity user = new RegisterUserEntity();
        user.setUserNo(generateUserNo());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : username);

        try {
            authUserMapper.insertUser(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(UserErrorCode.USERNAME_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }
        if (user.getId() == null) {
            throw new IllegalStateException("用户注册后未返回主键");
        }
        authUserMapper.insertUserRole(user.getId(), roleId);

        return new RegisterResponse(user.getId(), user.getUserNo(), user.getUsername(), user.getNickname());
    }

    public void logout(String refreshToken) {
        tokenService.logout(refreshToken);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        UserAuthRecord user = authUserMapper.findById(userId);
        if (user == null || isDeleted(user)) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        if (!passwordEncoder.matches(oldPassword, user.passwordHash())) {
            throw new BizException(UserErrorCode.PASSWORD_INCORRECT, HttpStatus.BAD_REQUEST);
        }

        authUserMapper.updatePasswordHashById(userId, passwordEncoder.encode(newPassword));
        tokenService.revokeAllSessionsByUserId(userId);
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

    private String generateUserNo() {
        int randomSuffix = (int) (Math.random() * 9000) + 1000;
        return "U" + System.currentTimeMillis() + randomSuffix;
    }
}
