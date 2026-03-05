package moe.hhm.shiori.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.PaymentErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.payment.domain.CdkCodeStatus;
import moe.hhm.shiori.payment.domain.TradePaymentStatus;
import moe.hhm.shiori.payment.domain.TradeRefundStatus;
import moe.hhm.shiori.payment.dto.RedeemCdkResponse;
import moe.hhm.shiori.payment.dto.WalletBalanceResponse;
import moe.hhm.shiori.payment.dto.WalletLedgerItemResponse;
import moe.hhm.shiori.payment.dto.WalletLedgerPageResponse;
import moe.hhm.shiori.payment.dto.admin.CdkItemResponse;
import moe.hhm.shiori.payment.dto.admin.CreateCdkBatchRequest;
import moe.hhm.shiori.payment.dto.admin.CreateCdkBatchResponse;
import moe.hhm.shiori.payment.dto.admin.ReconcileIssuePageResponse;
import moe.hhm.shiori.payment.dto.admin.ReconcileIssueResponse;
import moe.hhm.shiori.payment.dto.internal.RefundOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.ReleaseOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.ReserveOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.SettleOrderPaymentResponse;
import moe.hhm.shiori.payment.event.EventEnvelope;
import moe.hhm.shiori.payment.event.WalletBalanceChangedPayload;
import moe.hhm.shiori.payment.model.CdkBatchEntity;
import moe.hhm.shiori.payment.model.CdkCodeEntity;
import moe.hhm.shiori.payment.model.CdkCodeRecord;
import moe.hhm.shiori.payment.model.ReconcileIssueEntity;
import moe.hhm.shiori.payment.model.ReconcileIssueRecord;
import moe.hhm.shiori.payment.model.ReconcileRefundLedgerGapRecord;
import moe.hhm.shiori.payment.model.ReconcileTradeLedgerGapRecord;
import moe.hhm.shiori.payment.model.ReconcileWalletMismatchRecord;
import moe.hhm.shiori.payment.model.TradePaymentRecord;
import moe.hhm.shiori.payment.model.TradeRefundRecord;
import moe.hhm.shiori.payment.model.WalletAccountRecord;
import moe.hhm.shiori.payment.model.WalletLedgerRecord;
import moe.hhm.shiori.payment.repository.PaymentMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {

    private static final String BIZ_TYPE_ORDER = "ORDER";
    private static final String BIZ_TYPE_CDK = "CDK";

    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentMapper paymentMapper,
                          ObjectMapper objectMapper) {
        this.paymentMapper = paymentMapper;
        this.objectMapper = objectMapper;
    }

    public WalletBalanceResponse getWalletBalance(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        WalletAccountRecord wallet = paymentMapper.findWalletByUserId(userId);
        long available = wallet == null || wallet.availableBalanceCent() == null ? 0L : wallet.availableBalanceCent();
        long frozen = wallet == null || wallet.frozenBalanceCent() == null ? 0L : wallet.frozenBalanceCent();
        return new WalletBalanceResponse(available, frozen, available + frozen);
    }

    public WalletLedgerPageResponse listWalletLedgerByUser(Long userId,
                                                            String bizType,
                                                            String bizNo,
                                                            String changeType,
                                                            LocalDateTime createdFrom,
                                                            LocalDateTime createdTo,
                                                            int page,
                                                            int size) {
        if (userId == null || userId <= 0) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;

        String normalizedBizType = normalizeOptionalCode(bizType);
        String normalizedBizNo = normalizeOptionalCode(bizNo);
        String normalizedChangeType = normalizeOptionalCode(changeType);
        long total = paymentMapper.countWalletLedgerByUser(userId,
                normalizedBizType,
                normalizedBizNo,
                normalizedChangeType,
                createdFrom,
                createdTo);
        List<WalletLedgerItemResponse> items = paymentMapper.listWalletLedgerByUser(
                        userId,
                        normalizedBizType,
                        normalizedBizNo,
                        normalizedChangeType,
                        createdFrom,
                        createdTo,
                        normalizedSize,
                        offset)
                .stream()
                .map(this::toLedgerItem)
                .toList();
        return new WalletLedgerPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public WalletLedgerPageResponse listWalletLedgerForAdmin(Long userId,
                                                             String bizType,
                                                             String bizNo,
                                                             String changeType,
                                                             LocalDateTime createdFrom,
                                                             LocalDateTime createdTo,
                                                             int page,
                                                             int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedBizType = normalizeOptionalCode(bizType);
        String normalizedBizNo = normalizeOptionalCode(bizNo);
        String normalizedChangeType = normalizeOptionalCode(changeType);
        long total = paymentMapper.countWalletLedgerForAdmin(
                userId,
                normalizedBizType,
                normalizedBizNo,
                normalizedChangeType,
                createdFrom,
                createdTo
        );
        List<WalletLedgerItemResponse> items = paymentMapper.listWalletLedgerForAdmin(
                        userId,
                        normalizedBizType,
                        normalizedBizNo,
                        normalizedChangeType,
                        createdFrom,
                        createdTo,
                        normalizedSize,
                        offset)
                .stream()
                .map(this::toLedgerItem)
                .toList();
        return new WalletLedgerPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public ReconcileIssuePageResponse listReconcileIssues(String status,
                                                          String issueType,
                                                          int page,
                                                          int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        int offset = (normalizedPage - 1) * normalizedSize;
        long total = paymentMapper.countReconcileIssues(normalizeOptionalCode(status), normalizeOptionalCode(issueType));
        List<ReconcileIssueResponse> items = paymentMapper.listReconcileIssues(
                        normalizeOptionalCode(status),
                        normalizeOptionalCode(issueType),
                        normalizedSize,
                        offset)
                .stream()
                .map(this::toReconcileIssue)
                .toList();
        return new ReconcileIssuePageResponse(total, normalizedPage, normalizedSize, items);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateReconcileIssueStatus(String issueNo,
                                           String fromStatus,
                                           String toStatus,
                                           Long operatorUserId) {
        if (!StringUtils.hasText(issueNo)
                || !isValidReconcileStatus(fromStatus)
                || !isValidReconcileStatus(toStatus)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        int affected = paymentMapper.updateReconcileIssueStatus(
                issueNo.trim(),
                fromStatus.trim().toUpperCase(Locale.ROOT),
                toStatus.trim().toUpperCase(Locale.ROOT),
                operatorUserId
        );
        if (affected == 0) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.CONFLICT, "对账问题状态流转失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public RedeemCdkResponse redeemCdk(Long userId, String code) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(code)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        String normalizedCode = normalizeCode(code);
        CdkCodeRecord cdk = paymentMapper.findCdkByHashForUpdate(sha256Hex(normalizedCode));
        if (cdk == null) {
            throw new BizException(PaymentErrorCode.PAYMENT_CDK_INVALID, HttpStatus.BAD_REQUEST);
        }
        if (Objects.equals(cdk.status(), CdkCodeStatus.USED.code())) {
            throw new BizException(PaymentErrorCode.PAYMENT_CDK_ALREADY_REDEEMED, HttpStatus.CONFLICT);
        }
        if (cdk.expireAt() != null && cdk.expireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(PaymentErrorCode.PAYMENT_CDK_EXPIRED, HttpStatus.BAD_REQUEST);
        }

        WalletAccountRecord wallet = ensureWalletForUpdate(userId);
        long availableBefore = safeNonNegative(wallet.availableBalanceCent());
        long frozenBefore = safeNonNegative(wallet.frozenBalanceCent());
        long amount = safePositive(cdk.amountCent());
        long availableAfter = checkedAdd(availableBefore, amount);
        paymentMapper.updateWalletBalance(userId, availableAfter, frozenBefore);
        appendLedger(userId, BIZ_TYPE_CDK, String.valueOf(cdk.id()), "CDK_REDEEM",
                amount, 0L, availableAfter, frozenBefore, "CDK兑换");
        appendWalletChangedOutbox(userId, "CDK:" + cdk.id());

        int affected = paymentMapper.markCdkRedeemed(
                cdk.id(),
                userId,
                LocalDateTime.now(),
                CdkCodeStatus.UNUSED.code(),
                CdkCodeStatus.USED.code()
        );
        if (affected == 0) {
            throw new BizException(PaymentErrorCode.PAYMENT_CDK_ALREADY_REDEEMED, HttpStatus.CONFLICT);
        }
        return new RedeemCdkResponse(amount, availableAfter, frozenBefore);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateCdkBatchResponse createCdkBatch(Long operatorUserId, CreateCdkBatchRequest request) {
        if (operatorUserId == null || operatorUserId <= 0 || request == null
                || request.quantity() == null || request.quantity() <= 0
                || request.amountCent() == null || request.amountCent() <= 0) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        if (request.expireAt() != null && !request.expireAt().isAfter(LocalDateTime.now())) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST, "expireAt必须晚于当前时间");
        }

        String batchNo = generateBatchNo();
        CdkBatchEntity batch = new CdkBatchEntity();
        batch.setBatchNo(batchNo);
        batch.setQuantity(request.quantity());
        batch.setAmountCent(request.amountCent());
        batch.setExpireAt(request.expireAt());
        batch.setCreatedByUserId(operatorUserId);
        paymentMapper.insertCdkBatch(batch);

        if (batch.getId() == null) {
            throw new IllegalStateException("创建CDK批次未返回主键");
        }

        List<CdkItemResponse> codes = new ArrayList<>(request.quantity());
        for (int i = 0; i < request.quantity(); i++) {
            String plainCode = generatePlainCode();
            CdkCodeEntity codeEntity = new CdkCodeEntity();
            codeEntity.setBatchId(batch.getId());
            codeEntity.setAmountCent(request.amountCent());
            codeEntity.setExpireAt(request.expireAt());
            codeEntity.setCodeHash(sha256Hex(plainCode));
            codeEntity.setCodeMask(maskCode(plainCode));
            try {
                paymentMapper.insertCdkCode(codeEntity);
            } catch (DuplicateKeyException ex) {
                i--;
                continue;
            }
            codes.add(new CdkItemResponse(plainCode, codeEntity.getCodeMask()));
        }
        return new CreateCdkBatchResponse(batchNo, request.quantity(), request.amountCent(), request.expireAt(), codes);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReserveOrderPaymentResponse reserveOrderPayment(String orderNo,
                                                           Long buyerUserId,
                                                           Long sellerUserId,
                                                           Long amountCent) {
        if (!StringUtils.hasText(orderNo)
                || buyerUserId == null || buyerUserId <= 0
                || sellerUserId == null || sellerUserId <= 0
                || amountCent == null || amountCent <= 0) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        TradePaymentRecord existed = paymentMapper.findTradeByOrderNoForUpdate(orderNo.trim());
        if (existed != null) {
            TradePaymentStatus status = TradePaymentStatus.fromCode(existed.status());
            if (status == TradePaymentStatus.RESERVED) {
                ensureReserveRequestMatchesTrade(orderNo.trim(), buyerUserId, sellerUserId, amountCent, existed);
                return new ReserveOrderPaymentResponse(orderNo.trim(), existed.paymentNo(), status.name(), true);
            }
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        WalletAccountRecord buyerWallet = ensureWalletForUpdate(buyerUserId);
        TradePaymentRecord latest = paymentMapper.findTradeByOrderNoForUpdate(orderNo.trim());
        if (latest != null) {
            TradePaymentStatus latestStatus = TradePaymentStatus.fromCode(latest.status());
            if (latestStatus == TradePaymentStatus.RESERVED) {
                ensureReserveRequestMatchesTrade(orderNo.trim(), buyerUserId, sellerUserId, amountCent, latest);
                return new ReserveOrderPaymentResponse(orderNo.trim(), latest.paymentNo(), latestStatus.name(), true);
            }
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        long availableBefore = safeNonNegative(buyerWallet.availableBalanceCent());
        long frozenBefore = safeNonNegative(buyerWallet.frozenBalanceCent());
        long amount = safePositive(amountCent);
        if (availableBefore < amount) {
            throw new BizException(PaymentErrorCode.PAYMENT_BALANCE_NOT_ENOUGH, HttpStatus.CONFLICT);
        }

        long availableAfter = availableBefore - amount;
        long frozenAfter = checkedAdd(frozenBefore, amount);
        paymentMapper.updateWalletBalance(buyerUserId, availableAfter, frozenAfter);
        appendLedger(buyerUserId, BIZ_TYPE_ORDER, orderNo.trim(), "ORDER_RESERVE",
                -amount, amount, availableAfter, frozenAfter, "订单托管冻结");
        appendWalletChangedOutbox(buyerUserId, orderNo.trim());

        String paymentNo = generatePaymentNo();
        try {
            paymentMapper.insertTradePayment(
                    orderNo.trim(),
                    paymentNo,
                    buyerUserId,
                    sellerUserId,
                    amount,
                    TradePaymentStatus.RESERVED.code(),
                    LocalDateTime.now()
            );
        } catch (DuplicateKeyException ex) {
            TradePaymentRecord duplicated = paymentMapper.findTradeByOrderNo(orderNo.trim());
            if (duplicated != null
                    && TradePaymentStatus.fromCode(duplicated.status()) == TradePaymentStatus.RESERVED) {
                ensureReserveRequestMatchesTrade(orderNo.trim(), buyerUserId, sellerUserId, amountCent, duplicated);
                return new ReserveOrderPaymentResponse(orderNo.trim(), duplicated.paymentNo(),
                        TradePaymentStatus.fromCode(duplicated.status()).name(), true);
            }
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        return new ReserveOrderPaymentResponse(orderNo.trim(), paymentNo, TradePaymentStatus.RESERVED.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public SettleOrderPaymentResponse settleOrderPayment(String orderNo,
                                                         String operatorType,
                                                         Long operatorUserId) {
        if (!StringUtils.hasText(orderNo) || !StringUtils.hasText(operatorType)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        TradePaymentRecord trade = paymentMapper.findTradeByOrderNoForUpdate(orderNo.trim());
        if (trade == null) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        TradePaymentStatus status = TradePaymentStatus.fromCode(trade.status());
        if (status == TradePaymentStatus.SETTLED) {
            return new SettleOrderPaymentResponse(orderNo.trim(), trade.paymentNo(), status.name(), true);
        }
        if (status != TradePaymentStatus.RESERVED) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        long amount = safePositive(trade.amountCent());

        WalletAccountRecord buyerWallet = ensureWalletForUpdate(trade.buyerUserId());
        long buyerAvailable = safeNonNegative(buyerWallet.availableBalanceCent());
        long buyerFrozen = safeNonNegative(buyerWallet.frozenBalanceCent());
        if (buyerFrozen < amount) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT, "买家冻结余额不足");
        }
        long buyerFrozenAfter = buyerFrozen - amount;
        paymentMapper.updateWalletBalance(trade.buyerUserId(), buyerAvailable, buyerFrozenAfter);
        appendLedger(trade.buyerUserId(), BIZ_TYPE_ORDER, orderNo.trim(), "ORDER_SETTLE_BUYER",
                0L, -amount, buyerAvailable, buyerFrozenAfter, "订单托管结算-买家扣减冻结");
        appendWalletChangedOutbox(trade.buyerUserId(), orderNo.trim());

        WalletAccountRecord sellerWallet = ensureWalletForUpdate(trade.sellerUserId());
        long sellerAvailable = safeNonNegative(sellerWallet.availableBalanceCent());
        long sellerFrozen = safeNonNegative(sellerWallet.frozenBalanceCent());
        long sellerAvailableAfter = checkedAdd(sellerAvailable, amount);
        paymentMapper.updateWalletBalance(trade.sellerUserId(), sellerAvailableAfter, sellerFrozen);
        appendLedger(trade.sellerUserId(), BIZ_TYPE_ORDER, orderNo.trim(), "ORDER_SETTLE_SELLER",
                amount, 0L, sellerAvailableAfter, sellerFrozen,
                "订单托管结算-卖家入账, operatorType=" + operatorType.trim().toUpperCase(Locale.ROOT)
                        + ", operatorUserId=" + (operatorUserId == null ? "" : operatorUserId));
        appendWalletChangedOutbox(trade.sellerUserId(), orderNo.trim());

        int affected = paymentMapper.markTradeSettled(
                orderNo.trim(),
                TradePaymentStatus.RESERVED.code(),
                TradePaymentStatus.SETTLED.code(),
                LocalDateTime.now()
        );
        if (affected == 0) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        return new SettleOrderPaymentResponse(orderNo.trim(), trade.paymentNo(), TradePaymentStatus.SETTLED.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReleaseOrderPaymentResponse releaseOrderPayment(String orderNo, String reason) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        TradePaymentRecord trade = paymentMapper.findTradeByOrderNoForUpdate(orderNo.trim());
        if (trade == null) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        TradePaymentStatus status = TradePaymentStatus.fromCode(trade.status());
        if (status == TradePaymentStatus.RELEASED) {
            return new ReleaseOrderPaymentResponse(orderNo.trim(), trade.paymentNo(), status.name(), true);
        }
        if (status != TradePaymentStatus.RESERVED) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        long amount = safePositive(trade.amountCent());

        WalletAccountRecord buyerWallet = ensureWalletForUpdate(trade.buyerUserId());
        long buyerAvailable = safeNonNegative(buyerWallet.availableBalanceCent());
        long buyerFrozen = safeNonNegative(buyerWallet.frozenBalanceCent());
        if (buyerFrozen < amount) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT, "买家冻结余额不足");
        }
        long buyerAvailableAfter = checkedAdd(buyerAvailable, amount);
        long buyerFrozenAfter = buyerFrozen - amount;
        paymentMapper.updateWalletBalance(trade.buyerUserId(), buyerAvailableAfter, buyerFrozenAfter);
        appendLedger(trade.buyerUserId(), BIZ_TYPE_ORDER, orderNo.trim(), "ORDER_RELEASE",
                amount, -amount, buyerAvailableAfter, buyerFrozenAfter,
                StringUtils.hasText(reason) ? reason.trim() : "订单支付补偿释放");
        appendWalletChangedOutbox(trade.buyerUserId(), orderNo.trim());

        int affected = paymentMapper.markTradeReleased(
                orderNo.trim(),
                TradePaymentStatus.RESERVED.code(),
                TradePaymentStatus.RELEASED.code(),
                LocalDateTime.now()
        );
        if (affected == 0) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        return new ReleaseOrderPaymentResponse(orderNo.trim(), trade.paymentNo(), TradePaymentStatus.RELEASED.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundOrderPaymentResponse refundOrderPayment(String orderNo,
                                                         String refundNo,
                                                         String operatorType,
                                                         Long operatorUserId,
                                                         String reason) {
        if (!StringUtils.hasText(orderNo)
                || !StringUtils.hasText(refundNo)
                || !StringUtils.hasText(operatorType)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        String normalizedOrderNo = orderNo.trim();
        String normalizedRefundNo = refundNo.trim();
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : null;

        TradeRefundRecord existed = paymentMapper.findTradeRefundByRefundNoForUpdate(normalizedRefundNo);
        if (existed != null && !normalizedOrderNo.equals(existed.orderNo())) {
            throw new BizException(PaymentErrorCode.PAYMENT_REFUND_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        TradeRefundRecord byOrder = paymentMapper.findTradeRefundByOrderNoForUpdate(normalizedOrderNo);
        if (byOrder != null && !normalizedRefundNo.equals(byOrder.refundNo())) {
            throw new BizException(PaymentErrorCode.PAYMENT_REFUND_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        TradeRefundRecord current = existed != null ? existed : byOrder;
        if (current != null && TradeRefundStatus.fromCode(current.status()) == TradeRefundStatus.SUCCEEDED) {
            return new RefundOrderPaymentResponse(normalizedOrderNo, current.refundNo(), current.paymentNo(),
                    TradeRefundStatus.SUCCEEDED.name(), true);
        }

        TradePaymentRecord trade = paymentMapper.findTradeByOrderNoForUpdate(normalizedOrderNo);
        if (trade == null) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        TradePaymentStatus tradeStatus = TradePaymentStatus.fromCode(trade.status());
        if (tradeStatus == TradePaymentStatus.RESERVED) {
            ReleaseOrderPaymentResponse released = releaseOrderPayment(normalizedOrderNo,
                    StringUtils.hasText(normalizedReason) ? normalizedReason : "订单退款释放");
            if (current == null) {
                paymentMapper.insertTradeRefund(
                        normalizedRefundNo,
                        normalizedOrderNo,
                        trade.paymentNo(),
                        trade.buyerUserId(),
                        trade.sellerUserId(),
                        trade.amountCent(),
                        TradeRefundStatus.SUCCEEDED.code(),
                        normalizedReason
                );
            } else {
                paymentMapper.updateTradeRefundStatus(normalizedRefundNo,
                        TradeRefundStatus.PENDING_FUNDS.code(),
                        TradeRefundStatus.SUCCEEDED.code(),
                        normalizedReason);
            }
            return new RefundOrderPaymentResponse(normalizedOrderNo, normalizedRefundNo, released.paymentNo(),
                    TradeRefundStatus.SUCCEEDED.name(), false);
        }

        if (tradeStatus != TradePaymentStatus.SETTLED) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        long amount = safePositive(trade.amountCent());
        WalletAccountRecord sellerWallet = ensureWalletForUpdate(trade.sellerUserId());
        long sellerAvailable = safeNonNegative(sellerWallet.availableBalanceCent());
        long sellerFrozen = safeNonNegative(sellerWallet.frozenBalanceCent());
        if (sellerAvailable < amount) {
            if (current == null) {
                paymentMapper.insertTradeRefund(
                        normalizedRefundNo,
                        normalizedOrderNo,
                        trade.paymentNo(),
                        trade.buyerUserId(),
                        trade.sellerUserId(),
                        trade.amountCent(),
                        TradeRefundStatus.PENDING_FUNDS.code(),
                        normalizedReason
                );
            }
            return new RefundOrderPaymentResponse(normalizedOrderNo, normalizedRefundNo, trade.paymentNo(),
                    TradeRefundStatus.PENDING_FUNDS.name(), current != null);
        }

        WalletAccountRecord buyerWallet = ensureWalletForUpdate(trade.buyerUserId());
        long buyerAvailable = safeNonNegative(buyerWallet.availableBalanceCent());
        long buyerFrozen = safeNonNegative(buyerWallet.frozenBalanceCent());

        long sellerAvailableAfter = sellerAvailable - amount;
        long buyerAvailableAfter = checkedAdd(buyerAvailable, amount);

        paymentMapper.updateWalletBalance(trade.sellerUserId(), sellerAvailableAfter, sellerFrozen);
        appendLedger(trade.sellerUserId(), BIZ_TYPE_ORDER, normalizedOrderNo, "ORDER_REFUND_SELLER",
                -amount, 0L, sellerAvailableAfter, sellerFrozen,
                "订单退款扣减卖家可用, operatorType=" + operatorType.trim().toUpperCase(Locale.ROOT)
                        + ", operatorUserId=" + (operatorUserId == null ? "" : operatorUserId));
        appendWalletChangedOutbox(trade.sellerUserId(), normalizedOrderNo);

        paymentMapper.updateWalletBalance(trade.buyerUserId(), buyerAvailableAfter, buyerFrozen);
        appendLedger(trade.buyerUserId(), BIZ_TYPE_ORDER, normalizedOrderNo, "ORDER_REFUND_BUYER",
                amount, 0L, buyerAvailableAfter, buyerFrozen,
                StringUtils.hasText(normalizedReason) ? normalizedReason : "订单退款回退买家可用");
        appendWalletChangedOutbox(trade.buyerUserId(), normalizedOrderNo);

        if (current == null) {
            paymentMapper.insertTradeRefund(
                    normalizedRefundNo,
                    normalizedOrderNo,
                    trade.paymentNo(),
                    trade.buyerUserId(),
                    trade.sellerUserId(),
                    trade.amountCent(),
                    TradeRefundStatus.SUCCEEDED.code(),
                    normalizedReason
            );
        } else {
            paymentMapper.updateTradeRefundStatus(normalizedRefundNo,
                    TradeRefundStatus.PENDING_FUNDS.code(),
                    TradeRefundStatus.SUCCEEDED.code(),
                    normalizedReason);
        }

        return new RefundOrderPaymentResponse(normalizedOrderNo, normalizedRefundNo, trade.paymentNo(),
                TradeRefundStatus.SUCCEEDED.name(), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createReconcileIssue(String issueType, String bizNo, String severity, String detailJson) {
        if (!StringUtils.hasText(issueType) || !StringUtils.hasText(detailJson)) {
            return;
        }
        ReconcileIssueEntity entity = new ReconcileIssueEntity();
        entity.setIssueNo("RI" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 9999));
        entity.setIssueType(issueType.trim());
        entity.setBizNo(StringUtils.hasText(bizNo) ? bizNo.trim() : null);
        entity.setSeverity(StringUtils.hasText(severity) ? severity.trim().toUpperCase(Locale.ROOT) : "WARN");
        entity.setStatus("NEW");
        entity.setDetailJson(detailJson.trim());
        paymentMapper.insertReconcileIssue(entity);
    }

    public List<ReconcileWalletMismatchRecord> listWalletMismatchRecords(int size) {
        return paymentMapper.listWalletMismatchRecords(Math.max(size, 1));
    }

    public List<ReconcileTradeLedgerGapRecord> listTradeLedgerGapRecords(int size) {
        return paymentMapper.listTradeLedgerGapRecords(Math.max(size, 1));
    }

    public List<ReconcileRefundLedgerGapRecord> listRefundLedgerGapRecords(int size) {
        return paymentMapper.listRefundLedgerGapRecords(Math.max(size, 1));
    }

    private WalletAccountRecord ensureWalletForUpdate(Long userId) {
        WalletAccountRecord wallet = paymentMapper.findWalletByUserIdForUpdate(userId);
        if (wallet != null) {
            return wallet;
        }
        try {
            paymentMapper.insertWalletAccount(userId, 0L, 0L);
        } catch (DuplicateKeyException ignored) {
        }
        WalletAccountRecord created = paymentMapper.findWalletByUserIdForUpdate(userId);
        if (created == null) {
            throw new IllegalStateException("创建钱包账户失败, userId=" + userId);
        }
        return created;
    }

    private void appendLedger(Long userId,
                              String bizType,
                              String bizNo,
                              String changeType,
                              long deltaAvailable,
                              long deltaFrozen,
                              long availableAfter,
                              long frozenAfter,
                              String remark) {
        paymentMapper.insertWalletLedger(
                userId,
                bizType,
                bizNo,
                changeType,
                deltaAvailable,
                deltaFrozen,
                availableAfter,
                frozenAfter,
                remark
        );
    }

    private void appendWalletChangedOutbox(Long userId, String bizNo) {
        WalletAccountRecord wallet = paymentMapper.findWalletByUserId(userId);
        if (wallet == null) {
            return;
        }
        WalletBalanceChangedPayload payload = new WalletBalanceChangedPayload(
                userId,
                safeNonNegative(wallet.availableBalanceCent()),
                safeNonNegative(wallet.frozenBalanceCent()),
                StringUtils.hasText(bizNo) ? bizNo.trim() : "",
                Instant.now().toString()
        );
        String eventId = UUID.randomUUID().toString();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                "WalletBalanceChanged",
                String.valueOf(userId),
                Instant.now().toString(),
                objectMapper.valueToTree(payload)
        );
        try {
            String body = objectMapper.writeValueAsString(envelope);
            paymentMapper.insertWalletBalanceOutbox(
                    eventId,
                    userId,
                    payload.bizNo(),
                    body,
                    "PENDING",
                    0,
                    null,
                    null,
                    null
            );
        } catch (JacksonException e) {
            throw new IllegalStateException("构建余额变更事件失败", e);
        }
    }

    private WalletLedgerItemResponse toLedgerItem(WalletLedgerRecord record) {
        return new WalletLedgerItemResponse(
                record.id(),
                record.userId(),
                record.bizType(),
                record.bizNo(),
                record.changeType(),
                record.deltaAvailableCent(),
                record.deltaFrozenCent(),
                record.availableAfterCent(),
                record.frozenAfterCent(),
                record.remark(),
                record.createdAt()
        );
    }

    private ReconcileIssueResponse toReconcileIssue(ReconcileIssueRecord record) {
        return new ReconcileIssueResponse(
                record.issueNo(),
                record.issueType(),
                record.bizNo(),
                record.severity(),
                record.status(),
                record.detailJson(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private String normalizeOptionalCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean isValidReconcileStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "NEW".equals(normalized) || "ACKED".equals(normalized) || "RESOLVED".equals(normalized);
    }

    private String generatePaymentNo() {
        return "P" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100000, 999999);
    }

    private String generateBatchNo() {
        return "CB" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    private String generatePlainCode() {
        return "CDK-" + Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36).toUpperCase(Locale.ROOT);
    }

    private String maskCode(String code) {
        String normalized = normalizeCode(code);
        if (normalized.length() <= 4) {
            return "****";
        }
        if (normalized.length() <= 8) {
            return normalized.substring(0, 2) + "****" + normalized.substring(normalized.length() - 2);
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private long safeNonNegative(Long value) {
        if (value == null || value < 0) {
            throw new BizException(PaymentErrorCode.PAYMENT_BALANCE_INVALID, HttpStatus.CONFLICT);
        }
        return value;
    }

    private long safePositive(Long value) {
        if (value == null || value <= 0) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new BizException(PaymentErrorCode.PAYMENT_BALANCE_INVALID, HttpStatus.CONFLICT);
        }
    }

    private void ensureReserveRequestMatchesTrade(String orderNo,
                                                  Long buyerUserId,
                                                  Long sellerUserId,
                                                  Long amountCent,
                                                  TradePaymentRecord trade) {
        if (trade == null
                || !Objects.equals(orderNo, trade.orderNo())
                || !Objects.equals(buyerUserId, trade.buyerUserId())
                || !Objects.equals(sellerUserId, trade.sellerUserId())
                || !Objects.equals(amountCent, trade.amountCent())) {
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }
    }
}
