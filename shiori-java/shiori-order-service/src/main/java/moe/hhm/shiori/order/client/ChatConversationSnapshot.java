package moe.hhm.shiori.order.client;

public record ChatConversationSnapshot(
        Long conversationId,
        Long listingId,
        Long buyerId,
        Long sellerId
) {
}
