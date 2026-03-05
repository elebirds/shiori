package moe.hhm.shiori.user.profile.service;

import java.time.LocalDate;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.follow.service.UserFollowService;
import moe.hhm.shiori.user.profile.config.UserAvatarStorageProperties;
import moe.hhm.shiori.user.profile.dto.AvatarUploadResponse;
import moe.hhm.shiori.user.profile.dto.PublicUserProfileResponse;
import moe.hhm.shiori.user.profile.dto.UpdateProfileRequest;
import moe.hhm.shiori.user.profile.dto.UserProfileResponse;
import moe.hhm.shiori.user.profile.model.UserProfileRecord;
import moe.hhm.shiori.user.profile.repository.UserProfileMapper;
import moe.hhm.shiori.user.profile.storage.UserAvatarStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private UserAvatarStorageService userAvatarStorageService;

    @Mock
    private UserFollowService userFollowService;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        UserAvatarStorageProperties avatarStorageProperties = new UserAvatarStorageProperties();
        avatarStorageProperties.setPublicPathPrefix("/api/user/media/avatar/");
        userProfileService = new UserProfileService(userProfileMapper, userFollowService, userAvatarStorageService, avatarStorageProperties);
    }

    @Test
    void shouldGetProfile() {
        when(userProfileMapper.findByUserId(1L)).thenReturn(
                new UserProfileRecord(
                        1L,
                        "U202603030001",
                        "alice",
                        "Alice",
                        2,
                        LocalDate.of(2000, 1, 2),
                        "hello",
                        "avatar_1_202603_xxx.jpg"
                )
        );

        UserProfileResponse response = userProfileService.getMyProfile(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.nickname()).isEqualTo("Alice");
        assertThat(response.gender()).isEqualTo(2);
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(response.age()).isNotNull();
        assertThat(response.avatarUrl()).isEqualTo("/api/user/media/avatar/avatar_1_202603_xxx.jpg");
    }

    @Test
    void shouldGetPublicProfileByUserNo() {
        when(userProfileMapper.findByUserNo("U202603030001")).thenReturn(
                new UserProfileRecord(
                        1L,
                        "U202603030001",
                        "alice",
                        "Alice",
                        2,
                        LocalDate.of(2000, 1, 2),
                        "hello",
                        "avatar_1_202603_xxx.jpg"
                )
        );
        when(userFollowService.getFollowStats(1L, null)).thenReturn(
                new UserFollowService.FollowStats(6L, 9L, false)
        );

        PublicUserProfileResponse response = userProfileService.getProfileByUserNo("U202603030001");

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.userNo()).isEqualTo("U202603030001");
        assertThat(response.gender()).isEqualTo(2);
        assertThat(response.age()).isNotNull();
        assertThat(response.avatarUrl()).isEqualTo("/api/user/media/avatar/avatar_1_202603_xxx.jpg");
        assertThat(response.followerCount()).isEqualTo(6L);
        assertThat(response.followingCount()).isEqualTo(9L);
        assertThat(response.followedByCurrentUser()).isFalse();
    }

    @Test
    void shouldGetProfilesByUserIds() {
        when(userProfileMapper.findByUserIds(java.util.List.of(1L, 2L))).thenReturn(
                java.util.List.of(
                        new UserProfileRecord(
                                2L,
                                "U202603030002",
                                "bob",
                                "Bob",
                                1,
                                LocalDate.of(2001, 1, 1),
                                "hi",
                                "avatar_2.jpg"
                        ),
                        new UserProfileRecord(
                                1L,
                                "U202603030001",
                                "alice",
                                "Alice",
                                2,
                                LocalDate.of(2000, 1, 2),
                                "hello",
                                "avatar_1.jpg"
                        )
                )
        );

        java.util.List<PublicUserProfileResponse> response = userProfileService.getProfilesByUserIds(java.util.List.of(1L, 2L, 1L));

        assertThat(response).hasSize(2);
        assertThat(response.get(0).userId()).isEqualTo(1L);
        assertThat(response.get(1).userId()).isEqualTo(2L);
    }

    @Test
    void shouldUpdateProfile() {
        LocalDate birthDate = LocalDate.of(2001, 6, 1);
        when(userProfileMapper.findByUserId(1L))
                .thenReturn(new UserProfileRecord(
                        1L, "U202603030001", "alice", "Alice", 0, null, null, "avatar_old.jpg"
                ))
                .thenReturn(new UserProfileRecord(
                        1L, "U202603030001", "alice", "AliceNew", 1, birthDate, "new bio", "avatar_old.jpg"
                ));

        UserProfileResponse response = userProfileService.updateMyProfile(
                1L, new UpdateProfileRequest("AliceNew", 1, birthDate, "  new bio  ")
        );

        assertThat(response.nickname()).isEqualTo("AliceNew");
        assertThat(response.gender()).isEqualTo(1);
        assertThat(response.bio()).isEqualTo("new bio");
        verify(userProfileMapper).updateProfileByUserId(1L, "AliceNew", 1, birthDate, "new bio");
    }

    @Test
    void shouldUploadAvatar() {
        when(userProfileMapper.findByUserId(1L)).thenReturn(new UserProfileRecord(
                1L, "U202603030001", "alice", "Alice", 0, null, null, null
        ));
        when(userAvatarStorageService.uploadAvatar(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn("avatar_1_202603_abc.jpg");

        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        AvatarUploadResponse response = userProfileService.uploadMyAvatar(1L, file);

        assertThat(response.avatarUrl()).isEqualTo("/api/user/media/avatar/avatar_1_202603_abc.jpg");
        verify(userProfileMapper).updateAvatarByUserId(1L, "avatar_1_202603_abc.jpg");
    }

    @Test
    void shouldRejectInvalidGender() {
        when(userProfileMapper.findByUserId(1L)).thenReturn(
                new UserProfileRecord(1L, "U202603030001", "alice", "Alice", 0, null, null, null)
        );

        assertThatThrownBy(() -> userProfileService.updateMyProfile(
                1L, new UpdateProfileRequest("Alice", 8, null, null)
        )).isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectWhenProfileMissing() {
        when(userProfileMapper.findByUserId(99L)).thenReturn(null);

        assertThatThrownBy(() -> userProfileService.getMyProfile(99L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectWhenPublicProfileMissing() {
        when(userProfileMapper.findByUserNo("U404")).thenReturn(null);

        assertThatThrownBy(() -> userProfileService.getProfileByUserNo("U404"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectInvalidUserIdsBatch() {
        assertThatThrownBy(() -> userProfileService.getProfilesByUserIds(java.util.List.of()))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> userProfileService.getProfilesByUserIds(java.util.List.of(1L, -1L)))
                .isInstanceOf(BizException.class);
    }
}
