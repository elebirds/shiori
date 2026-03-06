package moe.hhm.shiori.product.controller.v2;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.product.dto.v2.ProductMetaCampusResponse;
import moe.hhm.shiori.product.dto.v2.ProductMetaCategoryResponse;
import moe.hhm.shiori.product.dto.v2.ProductMetaSubCategoryResponse;
import moe.hhm.shiori.product.service.ProductMetaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductMetaV2ControllerMvcTest {

    @Mock
    private ProductMetaService productMetaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProductMetaV2Controller controller = new ProductMetaV2Controller(productMetaService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldListEnabledCampuses() throws Exception {
        when(productMetaService.listEnabledCampuses())
                .thenReturn(List.of(
                        new ProductMetaCampusResponse("MAIN", "主校区"),
                        new ProductMetaCampusResponse("EAST", "东校区")
                ));

        mockMvc.perform(get("/api/v2/product/meta/campuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].campusCode").value("MAIN"))
                .andExpect(jsonPath("$.data[0].campusName").value("主校区"))
                .andExpect(jsonPath("$.data[1].campusCode").value("EAST"));

        verify(productMetaService).listEnabledCampuses();
    }

    @Test
    void shouldListEnabledCategoryTree() throws Exception {
        when(productMetaService.listEnabledCategories())
                .thenReturn(List.of(
                        new ProductMetaCategoryResponse(
                                "TEXTBOOK",
                                "教材",
                                List.of(
                                        new ProductMetaSubCategoryResponse("TEXTBOOK_UNSPEC", "未细分"),
                                        new ProductMetaSubCategoryResponse("TEXTBOOK_MATH", "数学教材")
                                )
                        )
                ));

        mockMvc.perform(get("/api/v2/product/meta/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].categoryCode").value("TEXTBOOK"))
                .andExpect(jsonPath("$.data[0].subCategories[0].subCategoryCode").value("TEXTBOOK_UNSPEC"))
                .andExpect(jsonPath("$.data[0].subCategories[1].subCategoryName").value("数学教材"));

        verify(productMetaService).listEnabledCategories();
    }
}
