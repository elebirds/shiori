package moe.hhm.shiori.user.auth.controller;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.user.auth.dto.AuthUserInfo;
import moe.hhm.shiori.user.auth.dto.TokenPairResponse;
import moe.hhm.shiori.user.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerMvcTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(objectMapper))
                .build();
    }

    @Test
    void shouldLoginSuccess() throws Exception {
        TokenPairResponse response = new TokenPairResponse(
                "access", 900, "refresh", 604800, "Bearer",
                new AuthUserInfo(1L, "U202603030001", "alice", List.of("ROLE_USER"))
        );
        when(authService.login("alice", "pwd", "127.0.0.1")).thenReturn(response);

        mockMvc.perform(post("/api/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"pwd"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.user.username").value("alice"));

        verify(authService).login("alice", "pwd", "127.0.0.1");
    }

    @Test
    void shouldReturnValidationErrorForBadLoginRequest() throws Exception {
        mockMvc.perform(post("/api/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000))
                .andExpect(jsonPath("$.data.errors").isArray());
    }

    @Test
    void shouldRefreshAndLogout() throws Exception {
        TokenPairResponse response = new TokenPairResponse(
                "new-access", 900, "new-refresh", 604800, "Bearer",
                new AuthUserInfo(1L, "U202603030001", "alice", List.of("ROLE_USER"))
        );
        when(authService.refresh("r1")).thenReturn(response);

        mockMvc.perform(post("/api/user/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"r1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));

        mockMvc.perform(post("/api/user/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"r1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        verify(authService).refresh("r1");
        verify(authService).logout("r1");
    }
}
