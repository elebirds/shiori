package moe.hhm.shiori.payment.dto;

import java.time.LocalDateTime;

public record WalletLedgerItemResponse(
        Long id,
        Long userId,
        String bizType,
        String bizNo,
        String changeType,
        Long deltaAvailableCent,
        Long deltaFrozenCent,
        Long availableAfterCent,
        Long frozenAfterCent,
        String remark,
        LocalDateTime createdAt
) {
}
