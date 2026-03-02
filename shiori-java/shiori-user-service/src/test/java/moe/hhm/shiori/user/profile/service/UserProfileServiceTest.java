package moe.hhm.shiori.user.profile.service;

import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.user.profile.dto.UpdateProfileRequest;
import moe.hhm.shiori.user.profile.dto.UserProfileResponse;
import moe.hhm.shiori.user.profile.model.UserProfileRecord;
import moe.hhm.shiori.user.profile.repository.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    void shouldGetProfile() {
        when(userProfileMapper.findByUserId(1L)).thenReturn(
                new UserProfileRecord(1L, "U202603030001", "alice", "Alice", "https://img/a.png")
        );

        UserProfileResponse response = userProfileService.getMyProfile(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.nickname()).isEqualTo("Alice");
    }

    @Test
    void shouldUpdateProfile() {
        when(userProfileMapper.findByUserId(1L))
                .thenReturn(new UserProfileRecord(1L, "U202603030001", "alice", "Alice", "https://img/a.png"))
                .thenReturn(new UserProfileRecord(1L, "U202603030001", "alice", "AliceNew", "https://img/b.png"));

        UserProfileResponse response = userProfileService.updateMyProfile(
                1L, new UpdateProfileRequest("AliceNew", "https://img/b.png")
        );

        assertThat(response.nickname()).isEqualTo("AliceNew");
        verify(userProfileMapper).updateProfileByUserId(1L, "AliceNew", "https://img/b.png");
    }

    @Test
    void shouldRejectWhenProfileMissing() {
        when(userProfileMapper.findByUserId(99L)).thenReturn(null);

        assertThatThrownBy(() -> userProfileService.getMyProfile(99L))
                .isInstanceOf(BizException.class);
    }
}
