package moe.hhm.shiori.product.admin.controller.v2;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.common.security.authz.AuthzHeaderNames;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.admin.dto.AdminProductMetaCampusResponse;
import moe.hhm.shiori.product.admin.dto.AdminProductMetaCategoryResponse;
import moe.hhm.shiori.product.admin.dto.AdminProductMetaSubCategoryResponse;
import moe.hhm.shiori.product.service.ProductMetaService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminProductMetaV2ControllerMvcTest {

    @Mock
    private ProductMetaService productMetaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        AdminProductMetaV2Controller controller = new AdminProductMetaV2Controller(productMetaService, new PermissionGuard());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldListAdminMetaTree() throws Exception {
        when(productMetaService.listCampusesForAdmin())
                .thenReturn(List.of(new AdminProductMetaCampusResponse(1L, "MAIN", "主校区", 1, 10)));
        when(productMetaService.listCategoriesForAdmin())
                .thenReturn(List.of(new AdminProductMetaCategoryResponse(
                        1L,
                        "TEXTBOOK",
                        "教材",
                        1,
                        10,
                        List.of(new AdminProductMetaSubCategoryResponse(2L, "TEXTBOOK", "TEXTBOOK_UNSPEC", "未细分", 1, 999))
                )));

        mockMvc.perform(get("/api/v2/admin/product-meta/campuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].campusCode").value("MAIN"));

        mockMvc.perform(get("/api/v2/admin/product-meta/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].categoryCode").value("TEXTBOOK"))
                .andExpect(jsonPath("$.data[0].subCategories[0].subCategoryCode").value("TEXTBOOK_UNSPEC"));

        verify(productMetaService).listCampusesForAdmin();
        verify(productMetaService).listCategoriesForAdmin();
    }

    @Test
    void shouldCreateCampusWhenPermissionGranted() throws Exception {
        when(productMetaService.createCampus(eq("MAIN"), eq("主校区"), eq(10)))
                .thenReturn(new AdminProductMetaCampusResponse(1L, "MAIN", "主校区", 1, 10));

        mockMvc.perform(post("/api/v2/admin/product-meta/campuses")
                        .header(AuthzHeaderNames.USER_AUTHZ_GRANTS, "product.meta.manage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "campusCode":"MAIN",
                                  "campusName":"主校区",
                                  "sortOrder":10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.campusCode").value("MAIN"));

        verify(productMetaService).createCampus("MAIN", "主校区", 10);
    }

    @Test
    void shouldRejectCreateCampusWhenPermissionDenied() throws Exception {
        mockMvc.perform(post("/api/v2/admin/product-meta/campuses")
                        .header(AuthzHeaderNames.USER_AUTHZ_GRANTS, "product.off_shelf")
                        .header(AuthzHeaderNames.USER_AUTHZ_DENIES, "product.meta.manage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "campusCode":"MAIN",
                                  "campusName":"主校区"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10004));
    }
}
