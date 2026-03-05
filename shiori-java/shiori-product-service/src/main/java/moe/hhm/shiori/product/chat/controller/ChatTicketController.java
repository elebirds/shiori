package moe.hhm.shiori.product.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Positive;
import moe.hhm.shiori.product.chat.dto.ChatTicketResponse;
import moe.hhm.shiori.product.chat.service.ChatTicketService;
import moe.hhm.shiori.product.security.CurrentUserSupport;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2/product/chat")
public class ChatTicketController {

    private final ChatTicketService chatTicketService;

    public ChatTicketController(ChatTicketService chatTicketService) {
        this.chatTicketService = chatTicketService;
    }

    @Operation(summary = "签发未下单咨询 Chat Ticket")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "签发成功",
                    content = @Content(schema = @Schema(implementation = ChatTicketResponse.class))),
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "503", description = "签发能力不可用")
    })
    @PostMapping("/ticket")
    public ChatTicketResponse issueTicket(@RequestParam @Positive Long listingId, Authentication authentication) {
        Long buyerId = CurrentUserSupport.requireUserId(authentication);
        return chatTicketService.issueTicket(listingId, buyerId);
    }
}
