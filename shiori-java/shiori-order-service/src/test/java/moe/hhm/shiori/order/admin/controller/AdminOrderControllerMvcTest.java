package moe.hhm.shiori.order.admin.controller;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.order.dto.OrderDetailResponse;
import moe.hhm.shiori.order.dto.OrderItemResponse;
import moe.hhm.shiori.order.dto.OrderOperateResponse;
import moe.hhm.shiori.order.dto.OrderPageResponse;
import moe.hhm.shiori.order.dto.OrderStatusAuditItemResponse;
import moe.hhm.shiori.order.dto.OrderStatusAuditPageResponse;
import moe.hhm.shiori.order.dto.OrderSummaryResponse;
import moe.hhm.shiori.order.service.OrderCommandService;
import moe.hhm.shiori.order.service.OrderService;
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
class AdminOrderControllerMvcTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderCommandService orderCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        AdminOrderController controller = new AdminOrderController(orderService, orderCommandService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldListOrdersForAdmin() throws Exception {
        when(orderService.listOrdersForAdmin("O001", "UNPAID", 1L, 2L, 1, 10)).thenReturn(new OrderPageResponse(
                1L,
                1,
                10,
                List.of(new OrderSummaryResponse("O001", "UNPAID", 1000L, 1, java.time.LocalDateTime.now(), null))
        ));

        mockMvc.perform(get("/api/admin/orders")
                        .param("page", "1")
                        .param("size", "10")
                        .param("orderNo", "O001")
                        .param("status", "UNPAID")
                        .param("buyerUserId", "1")
                        .param("sellerUserId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("O001"));
    }

    @Test
    void shouldGetOrderDetailForAdmin() throws Exception {
        when(orderService.getOrderDetailForAdmin("O001")).thenReturn(new OrderDetailResponse(
                "O001",
                1L,
                2L,
                "UNPAID",
                1000L,
                java.time.LocalDateTime.now(),
                null,
                null,
                List.of(new OrderItemResponse(1L, "P001", 10L, "S001", "标准版", "{}", 1000L, 1, 1000L))
        ));

        mockMvc.perform(get("/api/admin/orders/O001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("O001"));
    }

    @Test
    void shouldCancelOrderByAdmin() throws Exception {
        when(orderCommandService.cancelOrderAsAdmin(eq(99L), any(), eq("O001"), eq("manual")))
                .thenReturn(new OrderOperateResponse("O001", "CANCELED", false));

        mockMvc.perform(post("/api/admin/orders/O001/cancel")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "99", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"manual"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        verify(orderCommandService).cancelOrderAsAdmin(eq(99L), any(), eq("O001"), eq("manual"));
    }

    @Test
    void shouldDeliverOrderByAdmin() throws Exception {
        when(orderCommandService.deliverOrderAsAdmin(99L, "O001", "ship"))
                .thenReturn(new OrderOperateResponse("O001", "DELIVERING", false));

        mockMvc.perform(post("/api/admin/orders/O001/deliver")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "99", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"ship"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DELIVERING"));

        verify(orderCommandService).deliverOrderAsAdmin(99L, "O001", "ship");
    }

    @Test
    void shouldFinishOrderByAdmin() throws Exception {
        when(orderCommandService.finishOrderAsAdmin(99L, "O001", "done"))
                .thenReturn(new OrderOperateResponse("O001", "FINISHED", false));

        mockMvc.perform(post("/api/admin/orders/O001/finish")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "99", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"done"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("FINISHED"));

        verify(orderCommandService).finishOrderAsAdmin(99L, "O001", "done");
    }

    @Test
    void shouldListStatusAudits() throws Exception {
        when(orderService.listStatusAuditsForAdmin("O001", 1, 20))
                .thenReturn(new OrderStatusAuditPageResponse(
                        1L,
                        1,
                        20,
                        List.of(new OrderStatusAuditItemResponse(99L, "ADMIN", "PAID", "DELIVERING", "ship",
                                java.time.LocalDateTime.now()))
                ));

        mockMvc.perform(get("/api/admin/orders/O001/status-audits")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].toStatus").value("DELIVERING"));
    }
}
