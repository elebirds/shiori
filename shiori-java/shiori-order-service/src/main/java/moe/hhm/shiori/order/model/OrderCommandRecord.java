package moe.hhm.shiori.order.model;

import java.time.LocalDateTime;

public record OrderCommandRecord(
        Long id,
        String commandNo,
        String commandType,
        Long operatorUserId,
        String idempotencyKey,
        String orderNo,
        String status,
        String requestPayload,
        String progressPayload,
        Integer resultCode,
        String resultMessage,
        Integer retryCount,
        String lastError,
        LocalDateTime nextRetryAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
