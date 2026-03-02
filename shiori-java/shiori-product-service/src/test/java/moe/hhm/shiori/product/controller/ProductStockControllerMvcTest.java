package moe.hhm.shiori.product.controller;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.product.dto.StockDeductRequest;
import moe.hhm.shiori.product.dto.StockOperateResponse;
import moe.hhm.shiori.product.dto.StockReleaseRequest;
import moe.hhm.shiori.product.service.ProductStockService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductStockControllerMvcTest {

    @Mock
    private ProductStockService productStockService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ProductStockController controller = new ProductStockController(productStockService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldDeductStock() throws Exception {
        when(productStockService.deduct(any(StockDeductRequest.class)))
                .thenReturn(new StockOperateResponse(true, false, "BIZ-1", 10L, 2, 8));

        mockMvc.perform(post("/api/product/internal/stock/deduct")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuId":10,"quantity":2,"bizNo":"BIZ-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.currentStock").value(8));

        verify(productStockService).deduct(any(StockDeductRequest.class));
    }

    @Test
    void shouldReleaseStock() throws Exception {
        when(productStockService.release(any(StockReleaseRequest.class)))
                .thenReturn(new StockOperateResponse(true, false, "BIZ-2", 10L, 2, 12));

        mockMvc.perform(post("/api/product/internal/stock/release")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuId":10,"quantity":2,"bizNo":"BIZ-2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.currentStock").value(12));

        verify(productStockService).release(any(StockReleaseRequest.class));
    }
}
