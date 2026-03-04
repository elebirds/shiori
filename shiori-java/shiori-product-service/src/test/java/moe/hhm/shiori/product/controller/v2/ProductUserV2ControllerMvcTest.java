package moe.hhm.shiori.product.controller.v2;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.product.dto.v2.ProductV2PageResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2SummaryResponse;
import moe.hhm.shiori.product.service.ProductV2Service;
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
class ProductUserV2ControllerMvcTest {

    @Mock
    private ProductV2Service productV2Service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProductUserV2Controller controller = new ProductUserV2Controller(productV2Service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldListOnSaleProductsByOwner() throws Exception {
        when(productV2Service.listOnSaleProductsByOwner(1001L, "java", null, null, null,
                null, null, null, 1, 10))
                .thenReturn(new ProductV2PageResponse(
                        1L,
                        1,
                        10,
                        List.of(new ProductV2SummaryResponse(
                                1L,
                                "P001",
                                "Java Book",
                                "desc",
                                "product/1001/202603/a.jpg",
                                "http://cdn/a.jpg",
                                "ON_SALE",
                                "TEXTBOOK",
                                "GOOD",
                                "MEETUP",
                                "main_campus",
                                3900L,
                                3900L,
                                8
                        ))
                ));

        mockMvc.perform(get("/api/v2/product/users/1001/products")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("ON_SALE"));

        verify(productV2Service).listOnSaleProductsByOwner(1001L, "java", null, null, null,
                null, null, null, 1, 10);
    }
}
