package moe.hhm.shiori.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
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
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentMapper {

    @Select("""
            SELECT id,
                   user_id AS userId,
                   available_balance_cent AS availableBalanceCent,
                   frozen_balance_cent AS frozenBalanceCent,
                   version
            FROM p_wallet_account
            WHERE user_id = #{userId}
            LIMIT 1
            """)
    WalletAccountRecord findWalletByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id,
                   user_id AS userId,
                   available_balance_cent AS availableBalanceCent,
                   frozen_balance_cent AS frozenBalanceCent,
                   version
            FROM p_wallet_account
            WHERE user_id = #{userId}
            LIMIT 1
            FOR UPDATE
            """)
    WalletAccountRecord findWalletByUserIdForUpdate(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO p_wallet_account (
                user_id,
                available_balance_cent,
                frozen_balance_cent,
                version,
                created_at,
                updated_at
            ) VALUES (
                #{userId},
                #{availableBalanceCent},
                #{frozenBalanceCent},
                0,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertWalletAccount(@Param("userId") Long userId,
                            @Param("availableBalanceCent") Long availableBalanceCent,
                            @Param("frozenBalanceCent") Long frozenBalanceCent);

    @Update("""
            UPDATE p_wallet_account
            SET available_balance_cent = #{availableBalanceCent},
                frozen_balance_cent = #{frozenBalanceCent},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId}
            """)
    int updateWalletBalance(@Param("userId") Long userId,
                            @Param("availableBalanceCent") Long availableBalanceCent,
                            @Param("frozenBalanceCent") Long frozenBalanceCent);

    @Insert("""
            INSERT INTO p_wallet_ledger (
                user_id,
                biz_type,
                biz_no,
                change_type,
                delta_available_cent,
                delta_frozen_cent,
                available_after_cent,
                frozen_after_cent,
                remark,
                created_at
            ) VALUES (
                #{userId},
                #{bizType},
                #{bizNo},
                #{changeType},
                #{deltaAvailableCent},
                #{deltaFrozenCent},
                #{availableAfterCent},
                #{frozenAfterCent},
                #{remark},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertWalletLedger(@Param("userId") Long userId,
                           @Param("bizType") String bizType,
                           @Param("bizNo") String bizNo,
                           @Param("changeType") String changeType,
                           @Param("deltaAvailableCent") Long deltaAvailableCent,
                           @Param("deltaFrozenCent") Long deltaFrozenCent,
                           @Param("availableAfterCent") Long availableAfterCent,
                           @Param("frozenAfterCent") Long frozenAfterCent,
                           @Param("remark") String remark);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM p_wallet_ledger
            WHERE user_id = #{userId}
            <if test="bizType != null and bizType != ''">
              AND biz_type = #{bizType}
            </if>
            <if test="bizNo != null and bizNo != ''">
              AND biz_no LIKE CONCAT('%', #{bizNo}, '%')
            </if>
            <if test="changeType != null and changeType != ''">
              AND change_type = #{changeType}
            </if>
            <if test="createdFrom != null">
              AND created_at &gt;= #{createdFrom}
            </if>
            <if test="createdTo != null">
              AND created_at &lt;= #{createdTo}
            </if>
            </script>
            """)
    long countWalletLedgerByUser(@Param("userId") Long userId,
                                 @Param("bizType") String bizType,
                                 @Param("bizNo") String bizNo,
                                 @Param("changeType") String changeType,
                                 @Param("createdFrom") LocalDateTime createdFrom,
                                 @Param("createdTo") LocalDateTime createdTo);

    @Select("""
            <script>
            SELECT id,
                   user_id AS userId,
                   biz_type AS bizType,
                   biz_no AS bizNo,
                   change_type AS changeType,
                   delta_available_cent AS deltaAvailableCent,
                   delta_frozen_cent AS deltaFrozenCent,
                   available_after_cent AS availableAfterCent,
                   frozen_after_cent AS frozenAfterCent,
                   remark,
                   created_at AS createdAt
            FROM p_wallet_ledger
            WHERE user_id = #{userId}
            <if test="bizType != null and bizType != ''">
              AND biz_type = #{bizType}
            </if>
            <if test="bizNo != null and bizNo != ''">
              AND biz_no LIKE CONCAT('%', #{bizNo}, '%')
            </if>
            <if test="changeType != null and changeType != ''">
              AND change_type = #{changeType}
            </if>
            <if test="createdFrom != null">
              AND created_at &gt;= #{createdFrom}
            </if>
            <if test="createdTo != null">
              AND created_at &lt;= #{createdTo}
            </if>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<WalletLedgerRecord> listWalletLedgerByUser(@Param("userId") Long userId,
                                                     @Param("bizType") String bizType,
                                                     @Param("bizNo") String bizNo,
                                                     @Param("changeType") String changeType,
                                                     @Param("createdFrom") LocalDateTime createdFrom,
                                                     @Param("createdTo") LocalDateTime createdTo,
                                                     @Param("size") int size,
                                                     @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM p_wallet_ledger
            WHERE 1 = 1
            <if test="userId != null">
              AND user_id = #{userId}
            </if>
            <if test="bizType != null and bizType != ''">
              AND biz_type = #{bizType}
            </if>
            <if test="bizNo != null and bizNo != ''">
              AND biz_no LIKE CONCAT('%', #{bizNo}, '%')
            </if>
            <if test="changeType != null and changeType != ''">
              AND change_type = #{changeType}
            </if>
            <if test="createdFrom != null">
              AND created_at &gt;= #{createdFrom}
            </if>
            <if test="createdTo != null">
              AND created_at &lt;= #{createdTo}
            </if>
            </script>
            """)
    long countWalletLedgerForAdmin(@Param("userId") Long userId,
                                   @Param("bizType") String bizType,
                                   @Param("bizNo") String bizNo,
                                   @Param("changeType") String changeType,
                                   @Param("createdFrom") LocalDateTime createdFrom,
                                   @Param("createdTo") LocalDateTime createdTo);

    @Select("""
            <script>
            SELECT id,
                   user_id AS userId,
                   biz_type AS bizType,
                   biz_no AS bizNo,
                   change_type AS changeType,
                   delta_available_cent AS deltaAvailableCent,
                   delta_frozen_cent AS deltaFrozenCent,
                   available_after_cent AS availableAfterCent,
                   frozen_after_cent AS frozenAfterCent,
                   remark,
                   created_at AS createdAt
            FROM p_wallet_ledger
            WHERE 1 = 1
            <if test="userId != null">
              AND user_id = #{userId}
            </if>
            <if test="bizType != null and bizType != ''">
              AND biz_type = #{bizType}
            </if>
            <if test="bizNo != null and bizNo != ''">
              AND biz_no LIKE CONCAT('%', #{bizNo}, '%')
            </if>
            <if test="changeType != null and changeType != ''">
              AND change_type = #{changeType}
            </if>
            <if test="createdFrom != null">
              AND created_at &gt;= #{createdFrom}
            </if>
            <if test="createdTo != null">
              AND created_at &lt;= #{createdTo}
            </if>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<WalletLedgerRecord> listWalletLedgerForAdmin(@Param("userId") Long userId,
                                                       @Param("bizType") String bizType,
                                                       @Param("bizNo") String bizNo,
                                                       @Param("changeType") String changeType,
                                                       @Param("createdFrom") LocalDateTime createdFrom,
                                                       @Param("createdTo") LocalDateTime createdTo,
                                                       @Param("size") int size,
                                                       @Param("offset") int offset);

    @Select("""
            SELECT id,
                   order_no AS orderNo,
                   payment_no AS paymentNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   amount_cent AS amountCent,
                   status
            FROM p_trade_payment
            WHERE order_no = #{orderNo}
            LIMIT 1
            """)
    TradePaymentRecord findTradeByOrderNo(@Param("orderNo") String orderNo);

    @Select("""
            SELECT id,
                   order_no AS orderNo,
                   payment_no AS paymentNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   amount_cent AS amountCent,
                   status
            FROM p_trade_payment
            WHERE order_no = #{orderNo}
            LIMIT 1
            FOR UPDATE
            """)
    TradePaymentRecord findTradeByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Select("""
            SELECT id,
                   order_no AS orderNo,
                   payment_no AS paymentNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   amount_cent AS amountCent,
                   status
            FROM p_trade_payment
            WHERE payment_no = #{paymentNo}
            LIMIT 1
            """)
    TradePaymentRecord findTradeByPaymentNo(@Param("paymentNo") String paymentNo);

    @Insert("""
            INSERT INTO p_trade_payment (
                order_no,
                payment_no,
                buyer_user_id,
                seller_user_id,
                amount_cent,
                status,
                reserved_at,
                created_at,
                updated_at,
                version
            ) VALUES (
                #{orderNo},
                #{paymentNo},
                #{buyerUserId},
                #{sellerUserId},
                #{amountCent},
                #{status},
                #{reservedAt},
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3),
                0
            )
            """)
    int insertTradePayment(@Param("orderNo") String orderNo,
                           @Param("paymentNo") String paymentNo,
                           @Param("buyerUserId") Long buyerUserId,
                           @Param("sellerUserId") Long sellerUserId,
                           @Param("amountCent") Long amountCent,
                           @Param("status") Integer status,
                           @Param("reservedAt") LocalDateTime reservedAt);

    @Update("""
            UPDATE p_trade_payment
            SET status = #{settledStatus},
                settled_at = #{settledAt},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE order_no = #{orderNo}
              AND status = #{expectStatus}
            """)
    int markTradeSettled(@Param("orderNo") String orderNo,
                         @Param("expectStatus") Integer expectStatus,
                         @Param("settledStatus") Integer settledStatus,
                         @Param("settledAt") LocalDateTime settledAt);

    @Update("""
            UPDATE p_trade_payment
            SET status = #{releasedStatus},
                released_at = #{releasedAt},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE order_no = #{orderNo}
              AND status = #{expectStatus}
            """)
    int markTradeReleased(@Param("orderNo") String orderNo,
                          @Param("expectStatus") Integer expectStatus,
                          @Param("releasedStatus") Integer releasedStatus,
                          @Param("releasedAt") LocalDateTime releasedAt);

    @Select("""
            SELECT id,
                   refund_no AS refundNo,
                   order_no AS orderNo,
                   payment_no AS paymentNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   amount_cent AS amountCent,
                   status,
                   reason
            FROM p_trade_refund
            WHERE refund_no = #{refundNo}
            LIMIT 1
            """)
    TradeRefundRecord findTradeRefundByRefundNo(@Param("refundNo") String refundNo);

    @Select("""
            SELECT id,
                   refund_no AS refundNo,
                   order_no AS orderNo,
                   payment_no AS paymentNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   amount_cent AS amountCent,
                   status,
                   reason
            FROM p_trade_refund
            WHERE refund_no = #{refundNo}
            LIMIT 1
            FOR UPDATE
            """)
    TradeRefundRecord findTradeRefundByRefundNoForUpdate(@Param("refundNo") String refundNo);

    @Select("""
            SELECT id,
                   refund_no AS refundNo,
                   order_no AS orderNo,
                   payment_no AS paymentNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   amount_cent AS amountCent,
                   status,
                   reason
            FROM p_trade_refund
            WHERE order_no = #{orderNo}
            LIMIT 1
            FOR UPDATE
            """)
    TradeRefundRecord findTradeRefundByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Insert("""
            INSERT INTO p_trade_refund (
                refund_no,
                order_no,
                payment_no,
                buyer_user_id,
                seller_user_id,
                amount_cent,
                status,
                reason,
                created_at,
                updated_at
            ) VALUES (
                #{refundNo},
                #{orderNo},
                #{paymentNo},
                #{buyerUserId},
                #{sellerUserId},
                #{amountCent},
                #{status},
                #{reason},
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertTradeRefund(@Param("refundNo") String refundNo,
                          @Param("orderNo") String orderNo,
                          @Param("paymentNo") String paymentNo,
                          @Param("buyerUserId") Long buyerUserId,
                          @Param("sellerUserId") Long sellerUserId,
                          @Param("amountCent") Long amountCent,
                          @Param("status") Integer status,
                          @Param("reason") String reason);

    @Update("""
            UPDATE p_trade_refund
            SET status = #{toStatus},
                reason = #{reason},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE refund_no = #{refundNo}
              AND status = #{fromStatus}
            """)
    int updateTradeRefundStatus(@Param("refundNo") String refundNo,
                                @Param("fromStatus") Integer fromStatus,
                                @Param("toStatus") Integer toStatus,
                                @Param("reason") String reason);

    @Insert("""
            INSERT INTO p_wallet_balance_outbox (
                event_id,
                aggregate_type,
                aggregate_id,
                message_key,
                user_id,
                biz_no,
                payload,
                status,
                retry_count,
                last_error,
                next_retry_at,
                created_at,
                sent_at
            ) VALUES (
                #{eventId},
                #{aggregateType},
                #{aggregateId},
                #{messageKey},
                #{userId},
                #{bizNo},
                #{payload},
                #{status},
                #{retryCount},
                #{lastError},
                #{nextRetryAt},
                CURRENT_TIMESTAMP(3),
                #{sentAt}
            )
            """)
    int insertWalletBalanceOutbox(@Param("eventId") String eventId,
                                  @Param("aggregateType") String aggregateType,
                                  @Param("aggregateId") String aggregateId,
                                  @Param("messageKey") String messageKey,
                                  @Param("userId") Long userId,
                                  @Param("bizNo") String bizNo,
                                  @Param("payload") String payload,
                                  @Param("status") String status,
                                  @Param("retryCount") Integer retryCount,
                                  @Param("lastError") String lastError,
                                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                                  @Param("sentAt") LocalDateTime sentAt);

    @Insert("""
            INSERT INTO p_reconcile_issue (
                issue_no,
                issue_type,
                biz_no,
                severity,
                status,
                detail_json,
                created_at,
                updated_at
            ) VALUES (
                #{issueNo},
                #{issueType},
                #{bizNo},
                #{severity},
                #{status},
                #{detailJson},
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertReconcileIssue(ReconcileIssueEntity entity);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM p_reconcile_issue
            WHERE 1 = 1
            <if test="status != null and status != ''">
              AND status = #{status}
            </if>
            <if test="issueType != null and issueType != ''">
              AND issue_type = #{issueType}
            </if>
            </script>
            """)
    long countReconcileIssues(@Param("status") String status,
                              @Param("issueType") String issueType);

    @Select("""
            <script>
            SELECT id,
                   issue_no AS issueNo,
                   issue_type AS issueType,
                   biz_no AS bizNo,
                   severity,
                   status,
                   detail_json AS detailJson,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM p_reconcile_issue
            WHERE 1 = 1
            <if test="status != null and status != ''">
              AND status = #{status}
            </if>
            <if test="issueType != null and issueType != ''">
              AND issue_type = #{issueType}
            </if>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ReconcileIssueRecord> listReconcileIssues(@Param("status") String status,
                                                   @Param("issueType") String issueType,
                                                   @Param("size") int size,
                                                   @Param("offset") int offset);

    @Update("""
            UPDATE p_reconcile_issue
            SET status = #{toStatus},
                resolved_by_user_id = #{resolvedByUserId},
                resolved_at = CASE WHEN #{toStatus} = 'RESOLVED' THEN CURRENT_TIMESTAMP(3) ELSE resolved_at END,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE issue_no = #{issueNo}
              AND status = #{fromStatus}
            """)
    int updateReconcileIssueStatus(@Param("issueNo") String issueNo,
                                   @Param("fromStatus") String fromStatus,
                                   @Param("toStatus") String toStatus,
                                   @Param("resolvedByUserId") Long resolvedByUserId);

    @Select("""
            SELECT a.user_id AS userId,
                   a.available_balance_cent AS accountAvailableCent,
                   a.frozen_balance_cent AS accountFrozenCent,
                   l.available_after_cent AS ledgerAvailableCent,
                   l.frozen_after_cent AS ledgerFrozenCent
            FROM p_wallet_account a
            LEFT JOIN (
                SELECT ll.user_id,
                       ll.available_after_cent,
                       ll.frozen_after_cent
                FROM p_wallet_ledger ll
                JOIN (
                    SELECT user_id, MAX(id) AS max_id
                    FROM p_wallet_ledger
                    GROUP BY user_id
                ) latest ON latest.max_id = ll.id
            ) l ON l.user_id = a.user_id
            WHERE (l.user_id IS NULL AND (a.available_balance_cent <> 0 OR a.frozen_balance_cent <> 0))
               OR (l.user_id IS NOT NULL
                   AND (a.available_balance_cent <> l.available_after_cent OR a.frozen_balance_cent <> l.frozen_after_cent))
            ORDER BY a.id DESC
            LIMIT #{size}
            """)
    List<ReconcileWalletMismatchRecord> listWalletMismatchRecords(@Param("size") int size);

    @Select("""
            SELECT t.order_no AS orderNo,
                   t.payment_no AS paymentNo,
                   t.status AS tradeStatus
            FROM p_trade_payment t
            LEFT JOIN p_wallet_ledger l
                   ON l.biz_type = 'ORDER'
                  AND l.biz_no = t.order_no
            WHERE t.status IN (2, 3)
            GROUP BY t.order_no, t.payment_no, t.status
            HAVING COUNT(l.id) = 0
            ORDER BY MAX(t.id) DESC
            LIMIT #{size}
            """)
    List<ReconcileTradeLedgerGapRecord> listTradeLedgerGapRecords(@Param("size") int size);

    @Select("""
            SELECT r.refund_no AS refundNo,
                   r.order_no AS orderNo,
                   r.payment_no AS paymentNo
            FROM p_trade_refund r
            LEFT JOIN p_wallet_ledger lb
                   ON lb.biz_type = 'ORDER'
                  AND lb.biz_no = r.order_no
                  AND lb.change_type = 'ORDER_REFUND_BUYER'
            LEFT JOIN p_wallet_ledger ls
                   ON ls.biz_type = 'ORDER'
                  AND ls.biz_no = r.order_no
                  AND ls.change_type = 'ORDER_REFUND_SELLER'
            WHERE r.status = 2
            GROUP BY r.refund_no, r.order_no, r.payment_no
            HAVING COUNT(lb.id) = 0 OR COUNT(ls.id) = 0
            ORDER BY MAX(r.id) DESC
            LIMIT #{size}
            """)
    List<ReconcileRefundLedgerGapRecord> listRefundLedgerGapRecords(@Param("size") int size);

    @Insert("""
            INSERT INTO p_cdk_batch (
                batch_no,
                quantity,
                amount_cent,
                expire_at,
                created_by_user_id,
                created_at
            ) VALUES (
                #{batchNo},
                #{quantity},
                #{amountCent},
                #{expireAt},
                #{createdByUserId},
                CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertCdkBatch(CdkBatchEntity entity);

    @Insert("""
            INSERT INTO p_cdk_code (
                batch_id,
                code_hash,
                code_mask,
                amount_cent,
                expire_at,
                status,
                created_at
            ) VALUES (
                #{batchId},
                #{codeHash},
                #{codeMask},
                #{amountCent},
                #{expireAt},
                1,
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertCdkCode(CdkCodeEntity entity);

    @Select("""
            SELECT id,
                   batch_id AS batchId,
                   code_hash AS codeHash,
                   code_mask AS codeMask,
                   amount_cent AS amountCent,
                   expire_at AS expireAt,
                   status,
                   redeemed_by_user_id AS redeemedByUserId,
                   redeemed_at AS redeemedAt
            FROM p_cdk_code
            WHERE code_hash = #{codeHash}
            LIMIT 1
            FOR UPDATE
            """)
    CdkCodeRecord findCdkByHashForUpdate(@Param("codeHash") String codeHash);

    @Update("""
            UPDATE p_cdk_code
            SET status = #{usedStatus},
                redeemed_by_user_id = #{redeemedByUserId},
                redeemed_at = #{redeemedAt}
            WHERE id = #{id}
              AND status = #{expectStatus}
            """)
    int markCdkRedeemed(@Param("id") Long id,
                        @Param("redeemedByUserId") Long redeemedByUserId,
                        @Param("redeemedAt") LocalDateTime redeemedAt,
                        @Param("expectStatus") Integer expectStatus,
                        @Param("usedStatus") Integer usedStatus);
}
