package moe.hhm.shiori.payment.model;

import java.time.LocalDateTime;

public record WalletLedgerRecord(
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
