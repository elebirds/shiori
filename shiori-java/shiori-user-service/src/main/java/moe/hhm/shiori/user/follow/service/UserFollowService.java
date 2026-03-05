package moe.hhm.shiori.user.follow.service;

import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.follow.dto.FollowUserItemResponse;
import moe.hhm.shiori.user.follow.dto.FollowUserPageResponse;
import moe.hhm.shiori.user.follow.model.FollowUserRecord;
import moe.hhm.shiori.user.follow.repository.UserFollowMapper;
import moe.hhm.shiori.user.profile.config.UserAvatarStorageProperties;
import moe.hhm.shiori.user.profile.model.UserProfileRecord;
import moe.hhm.shiori.user.profile.repository.UserProfileMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserFollowService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final UserFollowMapper userFollowMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserAvatarStorageProperties avatarStorageProperties;

    public UserFollowService(UserFollowMapper userFollowMapper,
                             UserProfileMapper userProfileMapper,
                             UserAvatarStorageProperties avatarStorageProperties) {
        this.userFollowMapper = userFollowMapper;
        this.userProfileMapper = userProfileMapper;
        this.avatarStorageProperties = avatarStorageProperties;
    }

    public void followByUserNo(Long currentUserId, String targetUserNo) {
        UserProfileRecord source = requireProfileByUserId(currentUserId);
        UserProfileRecord target = requireProfileByUserNo(targetUserNo);
        if (source.userId().equals(target.userId())) {
            throw new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST);
        }
        userFollowMapper.insertFollow(source.userId(), target.userId());
    }

    public void unfollowByUserNo(Long currentUserId, String targetUserNo) {
        UserProfileRecord source = requireProfileByUserId(currentUserId);
        UserProfileRecord target = requireProfileByUserNo(targetUserNo);
        if (source.userId().equals(target.userId())) {
            throw new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST);
        }
        userFollowMapper.deleteFollow(source.userId(), target.userId());
    }

    public FollowUserPageResponse listFollowers(String userNo, Integer page, Integer size) {
        UserProfileRecord target = requireProfileByUserNo(userNo);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        long total = userFollowMapper.countFollowers(target.userId());
        List<FollowUserRecord> records = userFollowMapper.listFollowers(target.userId(), normalizedSize, offset);
        List<FollowUserItemResponse> items = records.stream().map(this::toItemResponse).toList();
        return new FollowUserPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public FollowUserPageResponse listFollowing(String userNo, Integer page, Integer size) {
        UserProfileRecord target = requireProfileByUserNo(userNo);
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        long total = userFollowMapper.countFollowing(target.userId());
        List<FollowUserRecord> records = userFollowMapper.listFollowing(target.userId(), normalizedSize, offset);
        List<FollowUserItemResponse> items = records.stream().map(this::toItemResponse).toList();
        return new FollowUserPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public FollowStats getFollowStats(Long targetUserId, Long currentUserId) {
        long followerCount = userFollowMapper.countFollowers(targetUserId);
        long followingCount = userFollowMapper.countFollowing(targetUserId);
        boolean followedByCurrentUser = currentUserId != null
                && currentUserId > 0
                && userFollowMapper.findFollowRelation(currentUserId, targetUserId) != null;
        return new FollowStats(followerCount, followingCount, followedByCurrentUser);
    }

    private FollowUserItemResponse toItemResponse(FollowUserRecord record) {
        return new FollowUserItemResponse(
                record.userId(),
                record.userNo(),
                record.nickname(),
                buildAvatarUrl(record.avatarUrl()),
                record.bio(),
                record.followedAt()
        );
    }

    private UserProfileRecord requireProfileByUserNo(String userNo) {
        UserProfileRecord profile = userProfileMapper.findByUserNo(userNo);
        if (profile == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return profile;
    }

    private UserProfileRecord requireProfileByUserId(Long userId) {
        UserProfileRecord profile = userProfileMapper.findByUserId(userId);
        if (profile == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return profile;
    }

    private int normalizePage(Integer page) {
        int normalized = page == null ? DEFAULT_PAGE : page;
        if (normalized < 1) {
            throw new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private int normalizeSize(Integer size) {
        int normalized = size == null ? DEFAULT_SIZE : size;
        if (normalized < 1 || normalized > MAX_SIZE) {
            throw new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST);
        }
        return normalized;
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

    public record FollowStats(
            long followerCount,
            long followingCount,
            boolean followedByCurrentUser
    ) {
    }
}
