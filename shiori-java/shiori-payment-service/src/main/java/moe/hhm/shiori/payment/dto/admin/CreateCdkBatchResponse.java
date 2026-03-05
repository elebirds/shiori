package moe.hhm.shiori.payment.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

public record CreateCdkBatchResponse(
        String batchNo,
        Integer quantity,
        Long amountCent,
        LocalDateTime expireAt,
        List<CdkItemResponse> codes
) {
}
