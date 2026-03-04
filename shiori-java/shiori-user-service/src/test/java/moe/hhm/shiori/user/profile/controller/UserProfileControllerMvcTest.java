package moe.hhm.shiori.user.profile.controller;

import java.time.LocalDate;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.user.profile.dto.AvatarUploadResponse;
import moe.hhm.shiori.user.profile.dto.UpdateProfileRequest;
import moe.hhm.shiori.user.profile.dto.UserProfileResponse;
import moe.hhm.shiori.user.profile.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerMvcTest {

    @Mock
    private UserProfileService userProfileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        UserProfileController controller = new UserProfileController(userProfileService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(objectMapper))
                .build();
    }

    @Test
    void shouldGetMe() throws Exception {
        when(userProfileService.getMyProfile(1L)).thenReturn(
                new UserProfileResponse(
                        1L,
                        "U202603030001",
                        "alice",
                        "Alice",
                        2,
                        LocalDate.of(2000, 1, 2),
                        26,
                        "hello",
                        "/api/user/media/avatar/avatar_1_202603_abc.jpg"
                )
        );

        mockMvc.perform(get("/api/user/me")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1", "N/A", java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.gender").value(2));

        verify(userProfileService).getMyProfile(1L);
    }

    @Test
    void shouldUpdateMe() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("AliceNew", 1, LocalDate.of(2001, 6, 1), "new bio");
        when(userProfileService.updateMyProfile(1L, request)).thenReturn(
                new UserProfileResponse(
                        1L,
                        "U202603030001",
                        "alice",
                        "AliceNew",
                        1,
                        LocalDate.of(2001, 6, 1),
                        24,
                        "new bio",
                        "/api/user/media/avatar/avatar_1_202603_abc.jpg"
                )
        );

        mockMvc.perform(put("/api/user/me")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1", "N/A", java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"AliceNew","gender":1,"birthDate":"2001-06-01","bio":"new bio"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("AliceNew"))
                .andExpect(jsonPath("$.data.bio").value("new bio"));

        verify(userProfileService).updateMyProfile(1L, request);
    }

    @Test
    void shouldUploadAvatar() throws Exception {
        when(userProfileService.uploadMyAvatar(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AvatarUploadResponse("/api/user/media/avatar/avatar_1_202603_abc.jpg"));

        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/user/media/avatar");
        builder.with(request -> {
            request.setMethod("POST");
            return request;
        });

        mockMvc.perform(builder
                        .file(file)
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1", "N/A", java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.avatarUrl").value("/api/user/media/avatar/avatar_1_202603_abc.jpg"));
    }

    @Test
    void shouldReturnAvatarBinary() throws Exception {
        when(userProfileService.loadAvatar("avatar_1_202603_abc.jpg"))
                .thenReturn(new moe.hhm.shiori.user.profile.storage.UserAvatarStorageService.AvatarObject(
                        new byte[]{1, 2, 3},
                        "image/jpeg"
                ));

        mockMvc.perform(get("/api/user/media/avatar/avatar_1_202603_abc.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}
