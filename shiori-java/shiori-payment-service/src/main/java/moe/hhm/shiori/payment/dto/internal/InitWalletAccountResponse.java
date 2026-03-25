package moe.hhm.shiori.payment.dto.internal;

public record InitWalletAccountResponse(
        Long userId,
        String status,
        boolean idempotent
) {
}
