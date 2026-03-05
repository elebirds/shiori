package moe.hhm.shiori.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.PaymentErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.payment.domain.CdkCodeStatus;
import moe.hhm.shiori.payment.domain.TradePaymentStatus;
import moe.hhm.shiori.payment.dto.RedeemCdkResponse;
import moe.hhm.shiori.payment.dto.WalletBalanceResponse;
import moe.hhm.shiori.payment.dto.admin.CdkItemResponse;
import moe.hhm.shiori.payment.dto.admin.CreateCdkBatchRequest;
import moe.hhm.shiori.payment.dto.admin.CreateCdkBatchResponse;
import moe.hhm.shiori.payment.dto.internal.ReleaseOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.ReserveOrderPaymentResponse;
import moe.hhm.shiori.payment.dto.internal.SettleOrderPaymentResponse;
import moe.hhm.shiori.payment.model.CdkBatchEntity;
import moe.hhm.shiori.payment.model.CdkCodeEntity;
import moe.hhm.shiori.payment.model.CdkCodeRecord;
import moe.hhm.shiori.payment.model.TradePaymentRecord;
import moe.hhm.shiori.payment.model.WalletAccountRecord;
import moe.hhm.shiori.payment.repository.PaymentMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PaymentService {

    private static final String BIZ_TYPE_ORDER = "ORDER";
    private static final String BIZ_TYPE_CDK = "CDK";

    private final PaymentMapper paymentMapper;

    public PaymentService(PaymentMapper paymentMapper) {
        this.paymentMapper = paymentMapper;
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
                return new ReserveOrderPaymentResponse(orderNo.trim(), existed.paymentNo(), status.name(), true);
            }
            throw new BizException(PaymentErrorCode.PAYMENT_TRADE_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        WalletAccountRecord buyerWallet = ensureWalletForUpdate(buyerUserId);
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
            if (duplicated != null) {
                return new ReserveOrderPaymentResponse(orderNo.trim(), duplicated.paymentNo(),
                        TradePaymentStatus.fromCode(duplicated.status()).name(), true);
            }
            throw ex;
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

        WalletAccountRecord sellerWallet = ensureWalletForUpdate(trade.sellerUserId());
        long sellerAvailable = safeNonNegative(sellerWallet.availableBalanceCent());
        long sellerFrozen = safeNonNegative(sellerWallet.frozenBalanceCent());
        long sellerAvailableAfter = checkedAdd(sellerAvailable, amount);
        paymentMapper.updateWalletBalance(trade.sellerUserId(), sellerAvailableAfter, sellerFrozen);
        appendLedger(trade.sellerUserId(), BIZ_TYPE_ORDER, orderNo.trim(), "ORDER_SETTLE_SELLER",
                amount, 0L, sellerAvailableAfter, sellerFrozen,
                "订单托管结算-卖家入账, operatorType=" + operatorType.trim().toUpperCase(Locale.ROOT)
                        + ", operatorUserId=" + (operatorUserId == null ? "" : operatorUserId));

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
        if (normalized.length() <= 8) {
            return normalized;
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
}
