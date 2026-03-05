package moe.hhm.shiori.user.admin.controller;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.user.admin.dto.AdminRoleResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserCapabilityBanResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserDetailResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserPageResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserStatusResponse;
import moe.hhm.shiori.user.admin.dto.AdminUserSummaryResponse;
import moe.hhm.shiori.user.admin.service.AdminUserService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerMvcTest {

    @Mock
    private AdminUserService adminUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        AdminUserController userController = new AdminUserController(adminUserService);
        AdminRoleController roleController = new AdminRoleController(adminUserService);
        AdminUserCapabilityController capabilityController = new AdminUserCapabilityController(adminUserService);
        InternalUserCapabilityController internalCapabilityController = new InternalUserCapabilityController(adminUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(userController, roleController, capabilityController, internalCapabilityController)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldListAdminUsers() throws Exception {
        when(adminUserService.listUsers("alice", "ENABLED", "ROLE_ADMIN", 1, 10)).thenReturn(
                new AdminUserPageResponse(
                        1L,
                        1,
                        10,
                        List.of(new AdminUserSummaryResponse(
                                1L,
                                "U001",
                                "alice",
                                "Alice",
                                "ENABLED",
                                List.of("ROLE_USER", "ROLE_ADMIN"),
                                null,
                                java.time.LocalDateTime.now()
                        ))
                )
        );

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "alice")
                        .param("status", "ENABLED")
                        .param("role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].username").value("alice"));
    }

    @Test
    void shouldGetAdminUserDetail() throws Exception {
        when(adminUserService.getUserDetail(1L)).thenReturn(new AdminUserDetailResponse(
                1L,
                "U001",
                "alice",
                "Alice",
                null,
                "ENABLED",
                0,
                null,
                false,
                null,
                null,
                List.of("ROLE_USER"),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now()
        ));

        mockMvc.perform(get("/api/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userNo").value("U001"));
    }

    @Test
    void shouldUpdateStatus() throws Exception {
        when(adminUserService.updateUserStatus(eq(99L), eq(1L), any())).thenReturn(new AdminUserStatusResponse(1L, "DISABLED", true));

        mockMvc.perform(post("/api/admin/users/1/status")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "99", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED","reason":"test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        verify(adminUserService).updateUserStatus(eq(99L), eq(1L), any());
    }

    @Test
    void shouldUpdateAdminRole() throws Exception {
        when(adminUserService.updateAdminRole(eq(99L), eq(1L), any())).thenReturn(new AdminUserStatusResponse(1L, "ENABLED", true));

        mockMvc.perform(put("/api/admin/users/1/admin-role")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "99", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grantAdmin":true,"reason":"grant"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.admin").value(true));
    }

    @Test
    void shouldListRoles() throws Exception {
        when(adminUserService.listRoles()).thenReturn(List.of(new AdminRoleResponse("ROLE_ADMIN", "管理员")));

        mockMvc.perform(get("/api/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].roleCode").value("ROLE_ADMIN"));
    }

    @Test
    void shouldUpsertCapabilityBan() throws Exception {
        when(adminUserService.upsertCapabilityBan(eq(99L), eq(1L), any())).thenReturn(
                new AdminUserCapabilityBanResponse(
                        10L,
                        1L,
                        "CHAT_SEND",
                        true,
                        "spam",
                        99L,
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now().plusDays(1),
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now()
                )
        );

        mockMvc.perform(post("/api/v2/admin/users/1/capability-bans")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "99", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"capability":"CHAT_SEND","reason":"spam"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capability").value("CHAT_SEND"))
                .andExpect(jsonPath("$.data.banned").value(true));
    }

    @Test
    void shouldDeleteCapabilityBan() throws Exception {
        when(adminUserService.removeCapabilityBan(eq(99L), eq(1L), eq("CHAT_SEND"), eq("recover"))).thenReturn(
                new AdminUserCapabilityBanResponse(
                        10L,
                        1L,
                        "CHAT_SEND",
                        false,
                        "recover",
                        99L,
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now()
                )
        );

        mockMvc.perform(delete("/api/v2/admin/users/1/capability-bans/CHAT_SEND")
                        .queryParam("reason", "recover")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "99", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.banned").value(false));
    }

    @Test
    void shouldListActiveCapabilitiesInternal() throws Exception {
        when(adminUserService.listActiveCapabilities(1L)).thenReturn(List.of("CHAT_SEND"));

        mockMvc.perform(get("/internal/users/1/capabilities/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.capabilities[0]").value("CHAT_SEND"));
    }
}
