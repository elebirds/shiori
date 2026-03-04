package moe.hhm.shiori.user.profile.service;

import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.profile.config.UserAvatarStorageProperties;
import moe.hhm.shiori.user.profile.dto.AvatarUploadResponse;
import moe.hhm.shiori.user.profile.dto.PublicUserProfileResponse;
import moe.hhm.shiori.user.profile.dto.UpdateProfileRequest;
import moe.hhm.shiori.user.profile.dto.UserProfileResponse;
import moe.hhm.shiori.user.profile.model.UserProfileRecord;
import moe.hhm.shiori.user.profile.repository.UserProfileMapper;
import moe.hhm.shiori.user.profile.storage.UserAvatarStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserProfileService {

    private static final Set<Integer> ALLOWED_GENDERS = Set.of(0, 1, 2, 9);
    private static final int MIN_AGE = 13;
    private static final int MAX_AGE = 120;
    private static final int MAX_BATCH_USER_IDS = 100;

    private final UserProfileMapper userProfileMapper;
    private final UserAvatarStorageService userAvatarStorageService;
    private final UserAvatarStorageProperties avatarStorageProperties;

    public UserProfileService(UserProfileMapper userProfileMapper,
                              UserAvatarStorageService userAvatarStorageService,
                              UserAvatarStorageProperties avatarStorageProperties) {
        this.userProfileMapper = userProfileMapper;
        this.userAvatarStorageService = userAvatarStorageService;
        this.avatarStorageProperties = avatarStorageProperties;
    }

    public UserProfileResponse getMyProfile(Long userId) {
        return toResponse(requireProfile(userId));
    }

    public PublicUserProfileResponse getProfileByUserNo(String userNo) {
        return toPublicResponse(requireProfileByUserNo(userNo));
    }

    public List<PublicUserProfileResponse> getProfilesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST);
        }
        LinkedHashSet<Long> normalizedSet = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId == null || userId <= 0) {
                throw new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST);
            }
            normalizedSet.add(userId);
        }
        if (normalizedSet.size() > MAX_BATCH_USER_IDS) {
            throw new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST);
        }

        List<Long> normalizedUserIds = new ArrayList<>(normalizedSet);
        List<UserProfileRecord> records = userProfileMapper.findByUserIds(normalizedUserIds);
        Map<Long, UserProfileRecord> byUserId = records.stream()
                .collect(Collectors.toMap(UserProfileRecord::userId, Function.identity(), (left, right) -> left));
        List<PublicUserProfileResponse> responses = new ArrayList<>();
        for (Long userId : normalizedUserIds) {
            UserProfileRecord record = byUserId.get(userId);
            if (record != null) {
                responses.add(toPublicResponse(record));
            }
        }
        return responses;
    }

    public UserProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        UserProfileRecord profile = requireProfile(userId);
        validateRequest(request);
        userProfileMapper.updateProfileByUserId(
                userId,
                request.nickname().trim(),
                request.gender(),
                request.birthDate(),
                normalizeNullableText(request.bio())
        );
        UserProfileRecord updated = userProfileMapper.findByUserId(userId);
        return toResponse(updated == null ? profile : updated);
    }

    public AvatarUploadResponse uploadMyAvatar(Long userId, MultipartFile file) {
        requireProfile(userId);
        String avatarKey = userAvatarStorageService.uploadAvatar(userId, file);
        userProfileMapper.updateAvatarByUserId(userId, avatarKey);
        return new AvatarUploadResponse(buildAvatarUrl(avatarKey));
    }

    public UserAvatarStorageService.AvatarObject loadAvatar(String avatarKey) {
        return userAvatarStorageService.loadAvatar(avatarKey);
    }

    private UserProfileRecord requireProfile(Long userId) {
        UserProfileRecord profile = userProfileMapper.findByUserId(userId);
        if (profile == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return profile;
    }

    private UserProfileRecord requireProfileByUserNo(String userNo) {
        UserProfileRecord profile = userProfileMapper.findByUserNo(userNo);
        if (profile == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return profile;
    }

    private void validateRequest(UpdateProfileRequest request) {
        if (!ALLOWED_GENDERS.contains(request.gender())) {
            throw new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST);
        }
        if (request.birthDate() == null) {
            return;
        }
        if (request.birthDate().isAfter(LocalDate.now())) {
            throw new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST);
        }
        int age = calculateAge(request.birthDate());
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST);
        }
    }

    private UserProfileResponse toResponse(UserProfileRecord record) {
        LocalDate birthDate = record.birthDate();
        return new UserProfileResponse(
                record.userId(),
                record.userNo(),
                record.username(),
                record.nickname(),
                record.gender(),
                birthDate,
                birthDate == null ? null : calculateAge(birthDate),
                record.bio(),
                buildAvatarUrl(record.avatarUrl())
        );
    }

    private PublicUserProfileResponse toPublicResponse(UserProfileRecord record) {
        LocalDate birthDate = record.birthDate();
        return new PublicUserProfileResponse(
                record.userId(),
                record.userNo(),
                record.username(),
                record.nickname(),
                buildAvatarUrl(record.avatarUrl()),
                record.gender(),
                birthDate == null ? null : calculateAge(birthDate),
                record.bio()
        );
    }

    private int calculateAge(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String buildAvatarUrl(String avatarValue) {
        if (!StringUtils.hasText(avatarValue)) {
            return null;
        }
        String trimmed = avatarValue.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String prefix = avatarStorageProperties.getPublicPathPrefix();
        if (!StringUtils.hasText(prefix)) {
            return "/api/user/media/avatar/" + trimmed;
        }
        return prefix.endsWith("/") ? prefix + trimmed : prefix + "/" + trimmed;
    }
}
