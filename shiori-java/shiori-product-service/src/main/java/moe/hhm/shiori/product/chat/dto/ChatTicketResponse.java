package moe.hhm.shiori.product.chat.dto;

public record ChatTicketResponse(
        String ticket,
        String expireAt,
        Long buyerId,
        Long sellerId,
        Long listingId,
        String jti
) {
}
