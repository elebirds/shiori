package moe.hhm.shiori.user.profile.service;

import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.profile.dto.UpdateProfileRequest;
import moe.hhm.shiori.user.profile.dto.UserProfileResponse;
import moe.hhm.shiori.user.profile.model.UserProfileRecord;
import moe.hhm.shiori.user.profile.repository.UserProfileMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final UserProfileMapper userProfileMapper;

    public UserProfileService(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    public UserProfileResponse getMyProfile(Long userId) {
        UserProfileRecord profile = userProfileMapper.findByUserId(userId);
        if (profile == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return toResponse(profile);
    }

    public UserProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        UserProfileRecord profile = userProfileMapper.findByUserId(userId);
        if (profile == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        userProfileMapper.updateProfileByUserId(userId, request.nickname(), request.avatarUrl());
        UserProfileRecord updated = userProfileMapper.findByUserId(userId);
        return toResponse(updated == null ? profile : updated);
    }

    private UserProfileResponse toResponse(UserProfileRecord record) {
        return new UserProfileResponse(
                record.userId(),
                record.userNo(),
                record.username(),
                record.nickname(),
                record.avatarUrl()
        );
    }
}
