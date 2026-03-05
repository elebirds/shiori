package moe.hhm.shiori.user.follow.controller;

import moe.hhm.shiori.common.error.UserErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.user.follow.dto.FollowUserItemResponse;
import moe.hhm.shiori.user.follow.dto.FollowUserPageResponse;
import moe.hhm.shiori.user.follow.service.UserFollowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserFollowControllerMvcTest {

    @Mock
    private UserFollowService userFollowService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        UserFollowController controller = new UserFollowController(userFollowService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(objectMapper))
                .build();
    }

    @Test
    void shouldFollowUser() throws Exception {
        mockMvc.perform(post("/api/user/follows/U202603010001")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        verify(userFollowService).followByUserNo(1L, "U202603010001");
    }

    @Test
    void shouldUnfollowUser() throws Exception {
        mockMvc.perform(delete("/api/user/follows/U202603010001")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        verify(userFollowService).unfollowByUserNo(1L, "U202603010001");
    }

    @Test
    void shouldListFollowers() throws Exception {
        when(userFollowService.listFollowers("U202603010001", 1, 20)).thenReturn(
                new FollowUserPageResponse(
                        1L,
                        1,
                        20,
                        List.of(new FollowUserItemResponse(
                                2L,
                                "U202603010002",
                                "Alice",
                                "/api/user/media/avatar/avatar_2.jpg",
                                "hello",
                                LocalDateTime.of(2026, 3, 6, 10, 0, 0)
                        ))
                )
        );

        mockMvc.perform(get("/api/user/profiles/U202603010001/followers")
                        .queryParam("page", "1")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].userNo").value("U202603010002"));
    }

    @Test
    void shouldListFollowing() throws Exception {
        when(userFollowService.listFollowing("U202603010001", 1, 20)).thenReturn(
                new FollowUserPageResponse(0L, 1, 20, List.of())
        );

        mockMvc.perform(get("/api/user/profiles/U202603010001/following")
                        .queryParam("page", "1")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void shouldReturn400ForInvalidPagination() throws Exception {
        when(userFollowService.listFollowers("U202603010001", 0, 20))
                .thenThrow(new BizException(UserErrorCode.PROFILE_INVALID, HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/user/profiles/U202603010001/followers")
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30013));
    }
}
