package moe.hhm.shiori.product.controller.v2;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.SpecItemResponse;
import moe.hhm.shiori.product.dto.SkuResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2DetailResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2PageResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2SummaryResponse;
import moe.hhm.shiori.product.service.ProductV2Service;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductV2ControllerMvcTest {

    @Mock
    private ProductV2Service productV2Service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ProductV2Controller controller = new ProductV2Controller(productV2Service, new PermissionGuard());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldCreateProductWithDetailHtml() throws Exception {
        when(productV2Service.createProduct(eq(1001L), any()))
                .thenReturn(new ProductWriteResponse(1L, "P001", "DRAFT"));

        mockMvc.perform(post("/api/v2/product/products")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Java Book",
                                  "description":"summary",
                                  "detailHtml":"<p><span style='font-size:18px'>detail</span></p><p><img src='product/1001/202603/detail.jpg' data-object-key='product/1001/202603/detail.jpg' style='width:50%'/></p>",
                                  "coverObjectKey":"product/1001/202603/a.jpg",
                                  "categoryCode":"TEXTBOOK",
                                  "subCategoryCode":"TEXTBOOK_UNSPEC",
                                  "conditionLevel":"GOOD",
                                  "tradeMode":"MEETUP",
                                  "campusCode":"main_campus",
                                  "skus":[{"specItems":[{"name":"版本","value":"标准版"}],"priceCent":3900,"stock":10}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void shouldUpdateProductWithDetailHtml() throws Exception {
        when(productV2Service.updateProduct(eq(1L), eq(1001L), eq(false), any()))
                .thenReturn(new ProductWriteResponse(1L, "P001", "DRAFT"));

        mockMvc.perform(put("/api/v2/product/products/1")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Java Book v2",
                                  "description":"summary v2",
                                  "detailHtml":"<p><strong>detail v2</strong></p>",
                                  "coverObjectKey":"product/1001/202603/a.jpg",
                                  "categoryCode":"TEXTBOOK",
                                  "subCategoryCode":"TEXTBOOK_UNSPEC",
                                  "conditionLevel":"GOOD",
                                  "tradeMode":"MEETUP",
                                  "campusCode":"main_campus",
                                  "skus":[{"id":10,"specItems":[{"name":"版本","value":"标准版"}],"priceCent":3900,"stock":8}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(productV2Service).updateProduct(eq(1L), eq(1001L), eq(false), any());
    }

    @Test
    void shouldReturnDetailHtmlInDetailResponse() throws Exception {
        when(productV2Service.getOnSaleProductDetail(1L)).thenReturn(new ProductV2DetailResponse(
                1L,
                "P001",
                1001L,
                "Java Book",
                "summary",
                "<p><span style=\"font-size:18px\">detail</span></p><p><img src=\"http://cdn/detail.jpg\" data-object-key=\"product/1001/202603/detail.jpg\" style=\"width:50%\"/></p>",
                "product/1001/202603/a.jpg",
                "http://cdn/a.jpg",
                "ON_SALE",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main_campus",
                3900L,
                3900L,
                8,
                List.of(new SkuResponse(
                        10L,
                        "S001",
                        "版本:标准版",
                        List.of(new SpecItemResponse("版本", "标准版")),
                        3900L,
                        8
                ))
        ));

        mockMvc.perform(get("/api/v2/product/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.detailHtml").value("<p><span style=\"font-size:18px\">detail</span></p><p><img src=\"http://cdn/detail.jpg\" data-object-key=\"product/1001/202603/detail.jpg\" style=\"width:50%\"/></p>"));
    }

    @Test
    void shouldPassKeywordFiltersAndSortToListEndpoint() throws Exception {
        when(productV2Service.listOnSaleProducts("Java", "TEXTBOOK", "TEXTBOOK_UNSPEC", "GOOD",
                "MEETUP", "main_campus", "MIN_PRICE", "ASC", 2, 5))
                .thenReturn(new ProductV2PageResponse(
                        1L,
                        2,
                        5,
                        List.of(new ProductV2SummaryResponse(
                                1L,
                                "P001",
                                1001L,
                                "Java Book",
                                "desc",
                                "product/1001/202603/a.jpg",
                                "http://cdn/a.jpg",
                                "ON_SALE",
                                "TEXTBOOK",
                                "TEXTBOOK_UNSPEC",
                                "GOOD",
                                "MEETUP",
                                "main_campus",
                                3900L,
                                3900L,
                                8
                        ))
                ));

        mockMvc.perform(get("/api/v2/product/products")
                        .param("keyword", "Java")
                        .param("categoryCode", "TEXTBOOK")
                        .param("subCategoryCode", "TEXTBOOK_UNSPEC")
                        .param("conditionLevel", "GOOD")
                        .param("tradeMode", "MEETUP")
                        .param("campusCode", "main_campus")
                        .param("sortBy", "MIN_PRICE")
                        .param("sortDir", "ASC")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(productV2Service).listOnSaleProducts("Java", "TEXTBOOK", "TEXTBOOK_UNSPEC", "GOOD",
                "MEETUP", "main_campus", "MIN_PRICE", "ASC", 2, 5);
    }
}
