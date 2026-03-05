package moe.hhm.shiori.payment.model;

public record WalletBalanceOutboxRecord(
        Long id,
        String eventId,
        String payload,
        String status,
        Integer retryCount
) {
}
