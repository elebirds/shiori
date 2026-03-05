package moe.hhm.shiori.payment.repository;

import java.time.LocalDateTime;
import moe.hhm.shiori.payment.model.CdkBatchEntity;
import moe.hhm.shiori.payment.model.CdkCodeEntity;
import moe.hhm.shiori.payment.model.CdkCodeRecord;
import moe.hhm.shiori.payment.model.TradePaymentRecord;
import moe.hhm.shiori.payment.model.WalletAccountRecord;
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
