package moe.hhm.shiori.product.controller;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.dto.ProductSearchReindexResponse;
import moe.hhm.shiori.product.service.ProductSearchReindexService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductSearchInternalControllerMvcTest {

    @Mock
    private ProductSearchReindexService reindexService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProductSearchInternalController controller = new ProductSearchInternalController(
                reindexService,
                new PermissionGuard()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldTriggerReindex() throws Exception {
        when(reindexService.reindexAllOnSaleProducts(2))
                .thenReturn(new ProductSearchReindexResponse(3L, 2, 3L));

        mockMvc.perform(post("/api/v2/product/internal/search/reindex")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .param("batchSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reindexedCount").value(3))
                .andExpect(jsonPath("$.data.batchCount").value(2))
                .andExpect(jsonPath("$.data.lastProductId").value(3));

        verify(reindexService).reindexAllOnSaleProducts(2);
    }
}
