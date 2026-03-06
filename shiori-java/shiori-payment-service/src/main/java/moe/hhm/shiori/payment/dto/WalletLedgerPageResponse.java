package moe.hhm.shiori.payment.dto;

import java.util.List;

public record WalletLedgerPageResponse(
        Long total,
        Integer page,
        Integer size,
        List<WalletLedgerItemResponse> items
) {
}
