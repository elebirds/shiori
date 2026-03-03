package moe.hhm.shiori.product.controller;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.product.dto.ProductDetailResponse;
import moe.hhm.shiori.product.dto.ProductPageResponse;
import moe.hhm.shiori.product.dto.ProductSummaryResponse;
import moe.hhm.shiori.product.dto.SkuResponse;
import moe.hhm.shiori.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductManageControllerMvcTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProductManageController controller = new ProductManageController(productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldListMyProducts() throws Exception {
        when(productService.listMyProducts(1001L, "java", "ON_SALE", 1, 10)).thenReturn(new ProductPageResponse(
                1L,
                1,
                10,
                List.of(new ProductSummaryResponse(
                        1L, "P001", "Java Book", "desc",
                        "product/1001/202603/a.jpg", "http://cdn/a.jpg", "ON_SALE"))
        ));

        mockMvc.perform(get("/api/product/my/products")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "java")
                        .param("status", "ON_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].productNo").value("P001"));
    }

    @Test
    void shouldGetMyProductDetail() throws Exception {
        when(productService.getMyProductDetail(eq(1L), eq(1001L), eq(false))).thenReturn(new ProductDetailResponse(
                1L, "P001", 1001L, "Java Book", "desc",
                "product/1001/202603/a.jpg", "http://cdn/a.jpg", "OFF_SHELF",
                List.of(new SkuResponse(10L, "S001", "标准版", "{}", 3900L, 8))
        ));

        mockMvc.perform(get("/api/product/my/products/1")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"))
                .andExpect(jsonPath("$.data.skus[0].skuName").value("标准版"));
    }
}

