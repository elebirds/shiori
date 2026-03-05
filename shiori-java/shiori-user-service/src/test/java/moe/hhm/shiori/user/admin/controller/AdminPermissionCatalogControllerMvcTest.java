package moe.hhm.shiori.user.admin.controller;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.user.authz.dto.AdminPermissionCatalogItemResponse;
import moe.hhm.shiori.user.authz.service.AdminPermissionCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminPermissionCatalogControllerMvcTest {

    @Mock
    private AdminPermissionCatalogService adminPermissionCatalogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminPermissionCatalogController controller = new AdminPermissionCatalogController(adminPermissionCatalogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldListPermissionCatalog() throws Exception {
        when(adminPermissionCatalogService.listCatalog()).thenReturn(List.of(
                new AdminPermissionCatalogItemResponse(
                        "chat.send",
                        "chat",
                        "send",
                        "发送聊天消息",
                        "允许发送聊天消息",
                        false
                )
        ));

        mockMvc.perform(get("/api/v2/admin/permissions/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].permissionCode").value("chat.send"))
                .andExpect(jsonPath("$.data[0].domain").value("chat"))
                .andExpect(jsonPath("$.data[0].deprecated").value(false));
    }
}
