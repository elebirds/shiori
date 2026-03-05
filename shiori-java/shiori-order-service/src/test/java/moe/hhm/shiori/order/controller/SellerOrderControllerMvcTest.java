package moe.hhm.shiori.order.controller;

import java.util.List;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.service.OrderCommandService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SellerOrderControllerMvcTest {

    @Mock
    private OrderCommandService orderCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        SellerOrderController controller = new SellerOrderController(orderCommandService, new PermissionGuard());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldDeliverOrderBySeller() throws Exception {
        when(orderCommandService.deliverOrderAsSeller(2001L, "O001", "ship"))
                .thenReturn(new OrderOperateResponse("O001", "DELIVERING", false));

        mockMvc.perform(post("/api/order/seller/orders/O001/deliver")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "2001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"ship"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DELIVERING"));

        verify(orderCommandService).deliverOrderAsSeller(2001L, "O001", "ship");
    }

    @Test
    void shouldFinishOrderBySeller() throws Exception {
        when(orderCommandService.finishOrderAsSeller(2001L, "O001", "done"))
                .thenReturn(new OrderOperateResponse("O001", "FINISHED", false));

        mockMvc.perform(post("/api/order/seller/orders/O001/finish")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "2001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"done"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("FINISHED"));

        verify(orderCommandService).finishOrderAsSeller(2001L, "O001", "done");
    }
}
