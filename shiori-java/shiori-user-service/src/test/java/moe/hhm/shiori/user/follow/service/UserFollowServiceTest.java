package moe.hhm.shiori.user.follow.service;

import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.follow.dto.FollowUserPageResponse;
import moe.hhm.shiori.user.follow.model.FollowUserRecord;
import moe.hhm.shiori.user.follow.repository.UserFollowMapper;
import moe.hhm.shiori.user.profile.config.UserAvatarStorageProperties;
import moe.hhm.shiori.user.profile.model.UserProfileRecord;
import moe.hhm.shiori.user.profile.repository.UserProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFollowServiceTest {

    @Mock
    private UserFollowMapper userFollowMapper;

    @Mock
    private UserProfileMapper userProfileMapper;

    private UserFollowService userFollowService;

    @BeforeEach
    void setUp() {
        UserAvatarStorageProperties avatarStorageProperties = new UserAvatarStorageProperties();
        avatarStorageProperties.setPublicPathPrefix("/api/user/media/avatar/");
        userFollowService = new UserFollowService(userFollowMapper, userProfileMapper, avatarStorageProperties);
    }

    @Test
    void shouldFollowUserByUserNo() {
        when(userProfileMapper.findByUserId(1L)).thenReturn(profile(1L, "U1"));
        when(userProfileMapper.findByUserNo("U2")).thenReturn(profile(2L, "U2"));

        userFollowService.followByUserNo(1L, "U2");

        verify(userFollowMapper).insertFollow(1L, 2L);
    }

    @Test
    void shouldRejectSelfFollow() {
        when(userProfileMapper.findByUserId(1L)).thenReturn(profile(1L, "U1"));
        when(userProfileMapper.findByUserNo("U1")).thenReturn(profile(1L, "U1"));

        assertThatThrownBy(() -> userFollowService.followByUserNo(1L, "U1"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldUnfollowUserByUserNo() {
        when(userProfileMapper.findByUserId(1L)).thenReturn(profile(1L, "U1"));
        when(userProfileMapper.findByUserNo("U2")).thenReturn(profile(2L, "U2"));

        userFollowService.unfollowByUserNo(1L, "U2");

        verify(userFollowMapper).deleteFollow(1L, 2L);
    }

    @Test
    void shouldListFollowers() {
        when(userProfileMapper.findByUserNo("U2")).thenReturn(profile(2L, "U2"));
        when(userFollowMapper.countFollowers(2L)).thenReturn(1L);
        when(userFollowMapper.listFollowers(2L, 20, 0)).thenReturn(List.of(
                new FollowUserRecord(
                        1L,
                        "U1",
                        "Alice",
                        "avatar_1.jpg",
                        "hello",
                        LocalDateTime.of(2026, 3, 6, 10, 0, 0)
                )
        ));

        FollowUserPageResponse response = userFollowService.listFollowers("U2", null, null);

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).avatarUrl()).isEqualTo("/api/user/media/avatar/avatar_1.jpg");
    }

    @Test
    void shouldRejectInvalidPagination() {
        when(userProfileMapper.findByUserNo("U2")).thenReturn(profile(2L, "U2"));

        assertThatThrownBy(() -> userFollowService.listFollowers("U2", 0, 20))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> userFollowService.listFollowing("U2", 1, 0))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> userFollowService.listFollowing("U2", 1, 51))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldReturnFollowStats() {
        when(userFollowMapper.countFollowers(2L)).thenReturn(8L);
        when(userFollowMapper.countFollowing(2L)).thenReturn(3L);
        when(userFollowMapper.findFollowRelation(1L, 2L)).thenReturn(1);

        UserFollowService.FollowStats stats = userFollowService.getFollowStats(2L, 1L);

        assertThat(stats.followerCount()).isEqualTo(8L);
        assertThat(stats.followingCount()).isEqualTo(3L);
        assertThat(stats.followedByCurrentUser()).isTrue();
    }

    private UserProfileRecord profile(Long userId, String userNo) {
        return new UserProfileRecord(
                userId,
                userNo,
                "user_" + userNo,
                "nick_" + userNo,
                0,
                null,
                null,
                null
        );
    }
}
