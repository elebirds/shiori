package moe.hhm.shiori.payment.model;

import java.time.LocalDateTime;

public record CdkCodeRecord(
        Long id,
        Long batchId,
        String codeHash,
        String codeMask,
        Long amountCent,
        LocalDateTime expireAt,
        Integer status,
        Long redeemedByUserId,
        LocalDateTime redeemedAt
) {
}
