package moe.hhm.shiori.payment.model;

public record ReconcileWalletMismatchRecord(
        Long userId,
        Long accountAvailableCent,
        Long accountFrozenCent,
        Long ledgerAvailableCent,
        Long ledgerFrozenCent
) {
}
