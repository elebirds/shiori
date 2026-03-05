package moe.hhm.shiori.product.admin.controller;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.admin.service.AdminProductService;
import moe.hhm.shiori.product.dto.ProductDetailResponse;
import moe.hhm.shiori.product.dto.ProductPageResponse;
import moe.hhm.shiori.product.dto.ProductSummaryResponse;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.SkuResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminProductControllerMvcTest {

    @Mock
    private AdminProductService adminProductService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        AdminProductController controller = new AdminProductController(adminProductService, new PermissionGuard());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldListAdminProducts() throws Exception {
        when(adminProductService.listProducts("java", "ON_SALE", 1001L, 1, 10)).thenReturn(new ProductPageResponse(
                1L,
                1,
                10,
                List.of(new ProductSummaryResponse(1L, "P001", "Java", "desc", null, null, "ON_SALE"))
        ));

        mockMvc.perform(get("/api/admin/products")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "java")
                        .param("status", "ON_SALE")
                        .param("ownerUserId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].productNo").value("P001"));
    }

    @Test
    void shouldGetAdminProductDetail() throws Exception {
        when(adminProductService.getProductDetail(1L, 99L)).thenReturn(new ProductDetailResponse(
                1L,
                "P001",
                1001L,
                "Java",
                "desc",
                null,
                null,
                "ON_SALE",
                List.of(new SkuResponse(10L, "S001", "标准版", "{}", 1000L, 10))
        ));

        mockMvc.perform(get("/api/admin/products/1")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "99", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.productNo").value("P001"));
    }

    @Test
    void shouldForceOffShelf() throws Exception {
        when(adminProductService.forceOffShelf(eq(1L), eq(99L), eq("manual")))
                .thenReturn(new ProductWriteResponse(1L, "P001", "OFF_SHELF"));

        mockMvc.perform(post("/api/admin/products/1/off-shelf")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "99", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"manual"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"));

        verify(adminProductService).forceOffShelf(eq(1L), eq(99L), any());
    }
}
