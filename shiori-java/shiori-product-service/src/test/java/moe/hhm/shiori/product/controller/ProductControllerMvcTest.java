package moe.hhm.shiori.product.controller;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.dto.CreateProductRequest;
import moe.hhm.shiori.product.dto.ProductDetailResponse;
import moe.hhm.shiori.product.dto.ProductPageResponse;
import moe.hhm.shiori.product.dto.ProductSummaryResponse;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.SkuInput;
import moe.hhm.shiori.product.dto.SkuResponse;
import moe.hhm.shiori.product.dto.UpdateProductRequest;
import moe.hhm.shiori.product.service.ProductService;
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
class ProductControllerMvcTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ProductController controller = new ProductController(productService, new PermissionGuard());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldListProductsAnonymous() throws Exception {
        when(productService.listOnSaleProducts("java", 1, 10)).thenReturn(new ProductPageResponse(
                1L,
                1,
                10,
                List.of(new ProductSummaryResponse(
                        1L, "P001", "Java Book", "desc",
                        "product/1001/202603/a.jpg", "http://cdn/a.jpg", "ON_SALE"))
        ));

        mockMvc.perform(get("/api/product/products")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("ON_SALE"));
    }

    @Test
    void shouldGetProductDetailAnonymous() throws Exception {
        when(productService.getOnSaleProductDetail(1L)).thenReturn(new ProductDetailResponse(
                1L, "P001", 1001L, "Java Book", "desc",
                "product/1001/202603/a.jpg", "http://cdn/a.jpg", "ON_SALE",
                List.of(new SkuResponse(10L, "S001", "标准版", "{}", 3900L, 8))
        ));

        mockMvc.perform(get("/api/product/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skus[0].skuName").value("标准版"));
    }

    @Test
    void shouldCreateProductWithAuthenticatedUser() throws Exception {
        when(productService.createProduct(eq(1001L), any(CreateProductRequest.class)))
                .thenReturn(new ProductWriteResponse(1L, "P001", "DRAFT"));

        mockMvc.perform(post("/api/product/products")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Java Book",
                                  "description":"desc",
                                  "coverObjectKey":"product/1001/202603/a.jpg",
                                  "skus":[{"skuName":"标准版","specJson":"{}","priceCent":3900,"stock":10}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        when(productService.updateProduct(eq(1L), eq(1001L), eq(false), any(UpdateProductRequest.class)))
                .thenReturn(new ProductWriteResponse(1L, "P001", "DRAFT"));

        mockMvc.perform(put("/api/product/products/1")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Java Book V2",
                                  "description":"desc",
                                  "coverObjectKey":"product/1001/202603/a.jpg",
                                  "skus":[{"id":10,"skuName":"标准版","specJson":"{}","priceCent":3900,"stock":8}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(productService).updateProduct(eq(1L), eq(1001L), eq(false), any(UpdateProductRequest.class));
    }
}
