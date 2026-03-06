package moe.hhm.shiori.payment.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import moe.hhm.shiori.payment.config.PaymentProperties;
import moe.hhm.shiori.payment.model.ReconcileRefundLedgerGapRecord;
import moe.hhm.shiori.payment.model.ReconcileTradeLedgerGapRecord;
import moe.hhm.shiori.payment.model.ReconcileWalletMismatchRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "payment.reconcile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconcileJob.class);

    private static final String ISSUE_TYPE_WALLET_MISMATCH = "WALLET_ACCOUNT_LEDGER_MISMATCH";
    private static final String ISSUE_TYPE_TRADE_LEDGER_GAP = "TRADE_LEDGER_GAP";
    private static final String ISSUE_TYPE_REFUND_LEDGER_GAP = "REFUND_LEDGER_GAP";

    private final PaymentService paymentService;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    public PaymentReconcileJob(PaymentService paymentService,
                               PaymentProperties paymentProperties,
                               ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${payment.reconcile.cron:0 30 2 * * *}", zone = "${payment.reconcile.zone:Asia/Shanghai}")
    public void reconcileDaily() {
        int batchSize = Math.max(paymentProperties.getReconcile().getBatchSize(), 1);
        reconcileWallets(batchSize);
        reconcileTradeLedgers(batchSize);
        reconcileRefundLedgers(batchSize);
    }

    private void reconcileWallets(int batchSize) {
        List<ReconcileWalletMismatchRecord> records = paymentService.listWalletMismatchRecords(batchSize);
        for (ReconcileWalletMismatchRecord record : records) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("userId", record.userId());
            detail.put("accountAvailableCent", record.accountAvailableCent());
            detail.put("accountFrozenCent", record.accountFrozenCent());
            detail.put("ledgerAvailableCent", record.ledgerAvailableCent());
            detail.put("ledgerFrozenCent", record.ledgerFrozenCent());
            paymentService.createReconcileIssue(
                    ISSUE_TYPE_WALLET_MISMATCH,
                    String.valueOf(record.userId()),
                    "WARN",
                    toJson(detail)
            );
        }
    }

    private void reconcileTradeLedgers(int batchSize) {
        List<ReconcileTradeLedgerGapRecord> records = paymentService.listTradeLedgerGapRecords(batchSize);
        for (ReconcileTradeLedgerGapRecord record : records) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("orderNo", record.orderNo());
            detail.put("paymentNo", record.paymentNo());
            detail.put("tradeStatus", record.tradeStatus());
            paymentService.createReconcileIssue(
                    ISSUE_TYPE_TRADE_LEDGER_GAP,
                    record.orderNo(),
                    "WARN",
                    toJson(detail)
            );
        }
    }

    private void reconcileRefundLedgers(int batchSize) {
        List<ReconcileRefundLedgerGapRecord> records = paymentService.listRefundLedgerGapRecords(batchSize);
        for (ReconcileRefundLedgerGapRecord record : records) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("refundNo", record.refundNo());
            detail.put("orderNo", record.orderNo());
            detail.put("paymentNo", record.paymentNo());
            paymentService.createReconcileIssue(
                    ISSUE_TYPE_REFUND_LEDGER_GAP,
                    record.refundNo(),
                    "WARN",
                    toJson(detail)
            );
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException ex) {
            log.warn("构建对账 issue detail 失败: {}", ex.getMessage());
            return "{}";
        }
    }
}
