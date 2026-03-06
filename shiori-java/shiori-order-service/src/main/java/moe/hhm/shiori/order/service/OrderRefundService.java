package moe.hhm.shiori.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.client.PaymentServiceClient;
import moe.hhm.shiori.order.client.RefundBalancePaymentSnapshot;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.domain.OrderPaymentMode;
import moe.hhm.shiori.order.domain.OrderRefundStatus;
import moe.hhm.shiori.order.domain.OrderStatus;
import moe.hhm.shiori.order.dto.OrderRefundPageResponse;
import moe.hhm.shiori.order.dto.OrderRefundResponse;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.model.OrderRefundEntity;
import moe.hhm.shiori.order.model.OrderRefundRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderRefundService {

    private static final Logger log = LoggerFactory.getLogger(OrderRefundService.class);

    private static final String ROLE_BUYER = "BUYER";
    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SYSTEM = "SYSTEM";

    private static final String ACTION_APPLY = "APPLY";
    private static final String ACTION_APPROVE = "APPROVE";
    private static final String ACTION_REJECT = "REJECT";
    private static final String ACTION_AUTO_APPROVE = "AUTO_APPROVE";
    private static final String ACTION_RETRY = "RETRY";

    private final OrderMapper orderMapper;
    private final PaymentServiceClient paymentServiceClient;
    private final OrderProperties orderProperties;

    public OrderRefundService(OrderMapper orderMapper,
                              PaymentServiceClient paymentServiceClient,
                              OrderProperties orderProperties) {
        this.orderMapper = orderMapper;
        this.paymentServiceClient = paymentServiceClient;
        this.orderProperties = orderProperties;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderRefundResponse applyRefund(Long buyerUserId, String orderNo, String reason) {
        OrderRecord order = requireOrder(orderNo);
        if (!order.buyerUserId().equals(buyerUserId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
        if (!isBalanceEscrowOrder(order.orderNo())) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_NOT_ALLOWED, HttpStatus.CONFLICT);
        }

        OrderRefundRecord existed = orderMapper.findOrderRefundByOrderNoForUpdate(order.orderNo());
        if (existed != null) {
            return toResponse(existed, true);
        }

        OrderStatus orderStatus = OrderStatus.fromCode(order.status());
        if (orderStatus != OrderStatus.PAID
                && orderStatus != OrderStatus.DELIVERING
                && orderStatus != OrderStatus.FINISHED) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_NOT_ALLOWED, HttpStatus.CONFLICT);
        }

        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : null;
        if (!StringUtils.hasText(normalizedReason)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST, "退款原因不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        OrderRefundEntity entity = new OrderRefundEntity();
        entity.setRefundNo(generateRefundNo());
        entity.setOrderNo(order.orderNo());
        entity.setBuyerUserId(order.buyerUserId());
        entity.setSellerUserId(order.sellerUserId());
        entity.setAmountCent(order.totalAmountCent());
        entity.setStatus(OrderRefundStatus.REQUESTED.name());
        entity.setApplyReason(normalizedReason);
        entity.setReviewDeadlineAt(orderStatus == OrderStatus.PAID
                ? now
                : now.plusHours(Math.max(orderProperties.getRefund().getReviewSlaHours(), 1)));
        entity.setAutoApproved(0);
        entity.setRetryCount(0);
        try {
            orderMapper.insertOrderRefund(entity);
        } catch (DuplicateKeyException ex) {
            OrderRefundRecord duplicated = orderMapper.findOrderRefundByOrderNoForUpdate(order.orderNo());
            if (duplicated != null) {
                return toResponse(duplicated, true);
            }
            throw ex;
        }
        insertRefundAudit(entity.getRefundNo(), entity.getOrderNo(), null, entity.getStatus(), buyerUserId, ROLE_BUYER, ACTION_APPLY,
                normalizedReason);
        syncOrderRefundSummary(entity.getOrderNo(), entity.getStatus(), entity.getRefundNo(), entity.getAmountCent());

        if (orderStatus == OrderStatus.PAID) {
            return approveInternal(entity.getRefundNo(), ROLE_BUYER, buyerUserId, buyerUserId, normalizedReason, false);
        }
        OrderRefundRecord created = orderMapper.findOrderRefundByRefundNo(entity.getRefundNo());
        return toResponse(created == null ? orderMapper.findOrderRefundByOrderNo(entity.getOrderNo()) : created, false);
    }

    public OrderRefundResponse getLatestRefundForBuyer(Long buyerUserId, String orderNo) {
        OrderRecord order = requireOrder(orderNo);
        if (!order.buyerUserId().equals(buyerUserId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
        OrderRefundRecord refund = orderMapper.findOrderRefundByOrderNo(order.orderNo());
        if (refund == null) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return toResponse(refund, false);
    }

    public OrderRefundPageResponse listRefundsForSeller(Long sellerUserId, String status, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedStatus = normalizeStatus(status);
        long total = orderMapper.countOrderRefundsBySeller(sellerUserId, normalizedStatus);
        List<OrderRefundResponse> items = orderMapper.listOrderRefundsBySeller(sellerUserId, normalizedStatus, normalizedSize, offset)
                .stream()
                .map(record -> toResponse(record, false))
                .toList();
        return new OrderRefundPageResponse(total, normalizedPage, normalizedSize, items);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderRefundResponse approveRefundAsSeller(Long sellerUserId, String refundNo, String reason) {
        OrderRefundRecord refund = requireRefundForUpdate(refundNo);
        if (!refund.sellerUserId().equals(sellerUserId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
        OrderRefundStatus status = requireRefundStatus(refund.status());
        if (status == OrderRefundStatus.SUCCEEDED || status == OrderRefundStatus.PENDING_FUNDS) {
            return toResponse(refund, true);
        }
        if (status != OrderRefundStatus.REQUESTED) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : "卖家审核通过";
        return approveInternal(refund.refundNo(), ROLE_SELLER, sellerUserId, sellerUserId, normalizedReason, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderRefundResponse rejectRefundAsSeller(Long sellerUserId, String refundNo, String reason) {
        OrderRefundRecord refund = requireRefundForUpdate(refundNo);
        if (!refund.sellerUserId().equals(sellerUserId)) {
            throw new BizException(OrderErrorCode.ORDER_NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
        OrderRefundStatus status = requireRefundStatus(refund.status());
        if (status == OrderRefundStatus.REJECTED) {
            return toResponse(refund, true);
        }
        if (status != OrderRefundStatus.REQUESTED) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : "卖家审核拒绝";
        int affected = orderMapper.rejectOrderRefund(
                refund.refundNo(),
                OrderRefundStatus.REQUESTED.name(),
                OrderRefundStatus.REJECTED.name(),
                sellerUserId,
                0,
                normalizedReason
        );
        if (affected == 0) {
            OrderRefundRecord latest = requireRefund(refund.refundNo());
            return toResponse(latest, true);
        }
        insertRefundAudit(refund.refundNo(), refund.orderNo(), OrderRefundStatus.REQUESTED.name(), OrderRefundStatus.REJECTED.name(),
                sellerUserId, ROLE_SELLER, ACTION_REJECT, normalizedReason);
        syncOrderRefundSummary(refund.orderNo(), OrderRefundStatus.REJECTED.name(), refund.refundNo(), refund.amountCent());
        return toResponse(requireRefund(refund.refundNo()), false);
    }

    public OrderRefundPageResponse listRefundsForAdmin(String refundNo,
                                                       String orderNo,
                                                       String status,
                                                       Long buyerUserId,
                                                       Long sellerUserId,
                                                       int page,
                                                       int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedStatus = normalizeStatus(status);
        String normalizedRefundNo = trimOptional(refundNo);
        String normalizedOrderNo = trimOptional(orderNo);
        long total = orderMapper.countOrderRefundsForAdmin(
                normalizedRefundNo,
                normalizedOrderNo,
                normalizedStatus,
                buyerUserId,
                sellerUserId
        );
        List<OrderRefundResponse> items = orderMapper.listOrderRefundsForAdmin(
                        normalizedRefundNo,
                        normalizedOrderNo,
                        normalizedStatus,
                        buyerUserId,
                        sellerUserId,
                        normalizedSize,
                        offset)
                .stream()
                .map(record -> toResponse(record, false))
                .toList();
        return new OrderRefundPageResponse(total, normalizedPage, normalizedSize, items);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderRefundResponse retryRefundAsAdmin(Long adminUserId, String refundNo) {
        return retryPendingRefund(refundNo, ROLE_ADMIN, adminUserId, adminUserId, "管理员手动重试");
    }

    @Transactional(rollbackFor = Exception.class)
    public void autoApproveExpiredRefunds() {
        int batchSize = Math.max(orderProperties.getRefund().getAutoApproveBatchSize(), 1);
        List<OrderRefundRecord> candidates = orderMapper.listOrderRefundsForAutoApprove(
                OrderRefundStatus.REQUESTED.name(),
                LocalDateTime.now(),
                batchSize
        );
        for (OrderRefundRecord candidate : candidates) {
            try {
                approveInternal(candidate.refundNo(), ROLE_SYSTEM, null, candidate.buyerUserId(), "审核超时自动同意", true);
            } catch (RuntimeException ex) {
                log.warn("自动同意退款失败, refundNo={}, err={}", candidate.refundNo(), ex.getMessage());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void retryPendingRefundsBySeller(Long sellerUserId) {
        if (sellerUserId == null || sellerUserId <= 0) {
            return;
        }
        int batchSize = Math.max(orderProperties.getRefund().getRetryBatchSize(), 1);
        List<OrderRefundRecord> records = orderMapper.listPendingFundsOrderRefundsBySeller(
                sellerUserId,
                OrderRefundStatus.PENDING_FUNDS.name(),
                batchSize
        );
        for (OrderRefundRecord record : records) {
            try {
                retryPendingRefund(record.refundNo(), ROLE_SYSTEM, null, record.buyerUserId(), "余额变更自动重试");
            } catch (RuntimeException ex) {
                log.warn("余额事件触发退款重试失败, refundNo={}, err={}", record.refundNo(), ex.getMessage());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderRefundResponse retryPendingRefund(String refundNo,
                                                  String operatorRole,
                                                  Long operatorUserId,
                                                  Long callerUserId,
                                                  String reason) {
        OrderRefundRecord refund = requireRefundForUpdate(refundNo);
        OrderRefundStatus status = requireRefundStatus(refund.status());
        if (status == OrderRefundStatus.SUCCEEDED) {
            return toResponse(refund, true);
        }
        if (status != OrderRefundStatus.PENDING_FUNDS) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        RefundBalancePaymentSnapshot paymentSnapshot = refundOrderPayment(refund, operatorRole, operatorUserId, callerUserId, reason);
        OrderRefundStatus paymentStatus = parsePaymentRefundStatus(paymentSnapshot.refundStatus());
        int affected = orderMapper.updateOrderRefundAfterRetry(
                refund.refundNo(),
                OrderRefundStatus.PENDING_FUNDS.name(),
                paymentStatus.name(),
                paymentSnapshot.paymentNo(),
                paymentStatus == OrderRefundStatus.PENDING_FUNDS ? "SELLER_BALANCE_NOT_ENOUGH" : null,
                1
        );
        if (affected == 0) {
            return toResponse(requireRefund(refund.refundNo()), true);
        }
        insertRefundAudit(
                refund.refundNo(),
                refund.orderNo(),
                OrderRefundStatus.PENDING_FUNDS.name(),
                paymentStatus.name(),
                operatorUserId,
                operatorRole,
                ACTION_RETRY,
                reason
        );
        syncOrderRefundSummary(refund.orderNo(), paymentStatus.name(), refund.refundNo(), refund.amountCent());
        syncOrderStatusAfterRefund(refund.orderNo(), paymentStatus, operatorUserId, operatorRole, reason);
        return toResponse(requireRefund(refund.refundNo()), false);
    }

    private OrderRefundResponse approveInternal(String refundNo,
                                                String operatorRole,
                                                Long operatorUserId,
                                                Long callerUserId,
                                                String reason,
                                                boolean autoApproved) {
        OrderRefundRecord refund = requireRefundForUpdate(refundNo);
        OrderRefundStatus status = requireRefundStatus(refund.status());
        if (status == OrderRefundStatus.SUCCEEDED || status == OrderRefundStatus.PENDING_FUNDS) {
            return toResponse(refund, true);
        }
        if (status != OrderRefundStatus.REQUESTED) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_STATUS_INVALID, HttpStatus.CONFLICT);
        }

        RefundBalancePaymentSnapshot paymentSnapshot = refundOrderPayment(refund, operatorRole, operatorUserId, callerUserId, reason);
        OrderRefundStatus paymentStatus = parsePaymentRefundStatus(paymentSnapshot.refundStatus());
        int affected = orderMapper.markOrderRefundReviewed(
                refund.refundNo(),
                OrderRefundStatus.REQUESTED.name(),
                paymentStatus.name(),
                operatorUserId,
                autoApproved ? 1 : 0,
                paymentSnapshot.paymentNo(),
                paymentStatus == OrderRefundStatus.PENDING_FUNDS ? "SELLER_BALANCE_NOT_ENOUGH" : null
        );
        if (affected == 0) {
            return toResponse(requireRefund(refund.refundNo()), true);
        }
        insertRefundAudit(
                refund.refundNo(),
                refund.orderNo(),
                OrderRefundStatus.REQUESTED.name(),
                paymentStatus.name(),
                operatorUserId,
                operatorRole,
                autoApproved ? ACTION_AUTO_APPROVE : ACTION_APPROVE,
                reason
        );
        syncOrderRefundSummary(refund.orderNo(), paymentStatus.name(), refund.refundNo(), refund.amountCent());
        syncOrderStatusAfterRefund(refund.orderNo(), paymentStatus, operatorUserId, operatorRole, reason);
        return toResponse(requireRefund(refund.refundNo()), false);
    }

    private RefundBalancePaymentSnapshot refundOrderPayment(OrderRefundRecord refund,
                                                            String operatorRole,
                                                            Long operatorUserId,
                                                            Long callerUserId,
                                                            String reason) {
        String normalizedOperatorType = StringUtils.hasText(operatorRole) ? operatorRole.trim().toUpperCase(Locale.ROOT) : ROLE_SYSTEM;
        Long requestUserId = callerUserId != null && callerUserId > 0 ? callerUserId : refund.buyerUserId();
        return paymentServiceClient.refundOrderPayment(
                refund.orderNo(),
                refund.refundNo(),
                normalizedOperatorType,
                operatorUserId,
                reason,
                requestUserId,
                List.of("ROLE_USER")
        );
    }

    private OrderRefundStatus parsePaymentRefundStatus(String status) {
        OrderRefundStatus parsed = OrderRefundStatus.fromCode(status);
        if (parsed == null || (parsed != OrderRefundStatus.SUCCEEDED && parsed != OrderRefundStatus.PENDING_FUNDS)) {
            throw new BizException(OrderErrorCode.ORDER_PAYMENT_RESPONSE_INVALID, HttpStatus.BAD_GATEWAY);
        }
        return parsed;
    }

    private OrderRefundStatus requireRefundStatus(String code) {
        OrderRefundStatus status = OrderRefundStatus.fromCode(code);
        if (status == null) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_STATUS_INVALID, HttpStatus.CONFLICT);
        }
        return status;
    }

    private void syncOrderRefundSummary(String orderNo, String refundStatus, String refundNo, Long refundAmountCent) {
        orderMapper.updateOrderRefundSummary(orderNo, refundStatus, refundNo, refundAmountCent);
    }

    private void syncOrderStatusAfterRefund(String orderNo,
                                            OrderRefundStatus paymentStatus,
                                            Long operatorUserId,
                                            String operatorRole,
                                            String reason) {
        if (paymentStatus != OrderRefundStatus.SUCCEEDED) {
            return;
        }
        OrderRecord before = requireOrder(orderNo);
        OrderStatus beforeStatus = OrderStatus.fromCode(before.status());
        if (beforeStatus == OrderStatus.REFUNDED) {
            return;
        }
        int affected = orderMapper.markOrderRefunded(
                orderNo,
                OrderRefundStatus.SUCCEEDED.name(),
                OrderStatus.REFUNDED.getCode(),
                OrderStatus.PAID.getCode(),
                OrderStatus.DELIVERING.getCode(),
                OrderStatus.FINISHED.getCode()
        );
        if (affected <= 0) {
            return;
        }
        OrderStatus fromStatus = beforeStatus == null ? OrderStatus.PAID : beforeStatus;
        orderMapper.insertStatusAuditLog(
                orderNo,
                operatorUserId,
                StringUtils.hasText(operatorRole) ? operatorRole : ROLE_SYSTEM,
                fromStatus.getCode(),
                OrderStatus.REFUNDED.getCode(),
                trimOptional(reason)
        );
    }

    private void insertRefundAudit(String refundNo,
                                   String orderNo,
                                   String fromStatus,
                                   String toStatus,
                                   Long operatorUserId,
                                   String operatorRole,
                                   String action,
                                   String reason) {
        orderMapper.insertOrderRefundAuditLog(
                refundNo,
                orderNo,
                fromStatus,
                toStatus,
                operatorUserId,
                operatorRole,
                action,
                trimOptional(reason)
        );
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        OrderRefundStatus parsed = OrderRefundStatus.fromCode(status);
        if (parsed == null) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_STATUS_INVALID, HttpStatus.BAD_REQUEST);
        }
        return parsed.name();
    }

    private String trimOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String generateRefundNo() {
        return "R" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100000, 999999);
    }

    private OrderRefundRecord requireRefundForUpdate(String refundNo) {
        if (!StringUtils.hasText(refundNo)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        OrderRefundRecord refund = orderMapper.findOrderRefundByRefundNoForUpdate(refundNo.trim());
        if (refund == null) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return refund;
    }

    private OrderRefundRecord requireRefund(String refundNo) {
        if (!StringUtils.hasText(refundNo)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        OrderRefundRecord refund = orderMapper.findOrderRefundByRefundNo(refundNo.trim());
        if (refund == null) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return refund;
    }

    private OrderRecord requireOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        OrderRecord order = orderMapper.findOrderByOrderNo(orderNo.trim());
        if (order == null || (order.isDeleted() != null && order.isDeleted() == 1)) {
            throw new BizException(OrderErrorCode.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return order;
    }

    private boolean isBalanceEscrowOrder(String orderNo) {
        String mode = orderMapper.findPaymentModeByOrderNo(orderNo);
        return OrderPaymentMode.fromCode(mode) == OrderPaymentMode.BALANCE_ESCROW;
    }

    private OrderRefundResponse toResponse(OrderRefundRecord record, boolean idempotent) {
        if (record == null) {
            throw new BizException(OrderErrorCode.ORDER_REFUND_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return new OrderRefundResponse(
                record.refundNo(),
                record.orderNo(),
                record.status(),
                record.amountCent(),
                record.applyReason(),
                record.rejectReason(),
                record.reviewedByUserId(),
                record.reviewDeadlineAt(),
                record.reviewedAt(),
                record.autoApproved() != null && record.autoApproved() == 1,
                record.paymentNo(),
                record.lastError(),
                record.retryCount(),
                record.createdAt(),
                record.updatedAt(),
                idempotent
        );
    }
}
