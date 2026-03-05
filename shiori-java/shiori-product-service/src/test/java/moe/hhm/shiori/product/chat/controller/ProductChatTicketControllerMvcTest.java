package moe.hhm.shiori.product.chat.controller;

import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.product.chat.dto.ChatTicketResponse;
import moe.hhm.shiori.product.chat.service.ChatTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductChatTicketControllerMvcTest {

    @Mock
    private ChatTicketService chatTicketService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ChatTicketController controller = new ChatTicketController(chatTicketService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldIssueTicket() throws Exception {
        when(chatTicketService.issueTicket(eq(101L), eq(1001L))).thenReturn(new ChatTicketResponse(
                "ticket-value",
                "2026-03-04T10:00:00Z",
                1001L,
                2002L,
                101L,
                "jti-1"
        ));

        mockMvc.perform(post("/api/v2/product/chat/ticket")
                        .param("listingId", "101")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ticket").value("ticket-value"))
                .andExpect(jsonPath("$.data.buyerId").value(1001))
                .andExpect(jsonPath("$.data.sellerId").value(2002));
    }

    @Test
    void shouldRejectOffSaleListing() throws Exception {
        when(chatTicketService.issueTicket(eq(101L), eq(1001L)))
                .thenThrow(new BizException(ProductErrorCode.PRODUCT_NOT_ON_SALE, HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v2/product/chat/ticket")
                        .param("listingId", "101")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ProductErrorCode.PRODUCT_NOT_ON_SALE.code()));
    }

    @Test
    void shouldRejectSelfChat() throws Exception {
        when(chatTicketService.issueTicket(eq(101L), eq(1001L)))
                .thenThrow(new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v2/product/chat/ticket")
                        .param("listingId", "101")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_PARAM.code()));
    }
}
