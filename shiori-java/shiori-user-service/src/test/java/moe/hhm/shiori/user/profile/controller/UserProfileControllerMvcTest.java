package moe.hhm.shiori.user.profile.controller;

import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.user.profile.dto.UpdateProfileRequest;
import moe.hhm.shiori.user.profile.dto.UserProfileResponse;
import moe.hhm.shiori.user.profile.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                new UserProfileResponse(1L, "U202603030001", "alice", "Alice", "https://img/a.png")
        );

        mockMvc.perform(get("/api/user/me")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1", "N/A", java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("alice"));

        verify(userProfileService).getMyProfile(1L);
    }

    @Test
    void shouldUpdateMe() throws Exception {
        when(userProfileService.updateMyProfile(1L, new UpdateProfileRequest(
                "AliceNew", "https://img/b.png"
        ))).thenReturn(new UserProfileResponse(1L, "U202603030001", "alice", "AliceNew", "https://img/b.png"));

        mockMvc.perform(put("/api/user/me")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1", "N/A", java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"AliceNew","avatarUrl":"https://img/b.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("AliceNew"));

        verify(userProfileService).updateMyProfile(
                1L,
                new UpdateProfileRequest("AliceNew", "https://img/b.png")
        );
    }
}
