package moe.hhm.shiori.order.repository;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.order.model.OrderEntity;
import moe.hhm.shiori.order.model.OrderItemEntity;
import moe.hhm.shiori.order.model.OrderItemRecord;
import moe.hhm.shiori.order.model.OrderOperateIdempotencyRecord;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.model.OrderStatusAuditRecord;
import moe.hhm.shiori.order.model.OutboxEventEntity;
import moe.hhm.shiori.order.model.OutboxEventRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper {

    @Insert("""
            INSERT INTO o_order (
                order_no,
                buyer_user_id,
                seller_user_id,
                status,
                total_amount_cent,
                item_count,
                payment_no,
                cancel_reason,
                timeout_at,
                paid_at,
                biz_source,
                chat_conversation_id,
                chat_listing_id,
                is_deleted,
                version,
                created_at,
                updated_at
            ) VALUES (
                #{orderNo},
                #{buyerUserId},
                #{sellerUserId},
                #{status},
                #{totalAmountCent},
                #{itemCount},
                #{paymentNo},
                #{cancelReason},
                #{timeoutAt},
                #{paidAt},
                #{bizSource},
                #{chatConversationId},
                #{chatListingId},
                0,
                0,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertOrder(OrderEntity orderEntity);

    @Insert("""
            <script>
            INSERT INTO o_order_item (
                order_id,
                order_no,
                product_id,
                product_no,
                sku_id,
                sku_no,
                sku_name,
                spec_json,
                price_cent,
                quantity,
                subtotal_cent,
                owner_user_id,
                created_at
            ) VALUES
            <foreach collection="items" item="item" separator=",">
                (
                    #{item.orderId},
                    #{item.orderNo},
                    #{item.productId},
                    #{item.productNo},
                    #{item.skuId},
                    #{item.skuNo},
                    #{item.skuName},
                    #{item.specJson},
                    #{item.priceCent},
                    #{item.quantity},
                    #{item.subtotalCent},
                    #{item.ownerUserId},
                    CURRENT_TIMESTAMP(3)
                )
            </foreach>
            </script>
            """)
    int batchInsertOrderItems(@Param("items") List<OrderItemEntity> items);

    @Insert("""
            INSERT INTO o_order_create_idempotency (
                buyer_user_id,
                idempotency_key,
                order_no,
                created_at
            ) VALUES (
                #{buyerUserId},
                #{idempotencyKey},
                #{orderNo},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertCreateIdempotency(@Param("buyerUserId") Long buyerUserId,
                                @Param("idempotencyKey") String idempotencyKey,
                                @Param("orderNo") String orderNo);

    @Select("""
            SELECT order_no
            FROM o_order_create_idempotency
            WHERE buyer_user_id = #{buyerUserId}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    String findOrderNoByBuyerAndIdempotencyKey(@Param("buyerUserId") Long buyerUserId,
                                               @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            INSERT INTO o_order_operate_idempotency (
                operator_user_id,
                operation_type,
                idempotency_key,
                order_no,
                created_at
            ) VALUES (
                #{operatorUserId},
                #{operationType},
                #{idempotencyKey},
                #{orderNo},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertOperateIdempotency(@Param("operatorUserId") Long operatorUserId,
                                 @Param("operationType") String operationType,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("orderNo") String orderNo);

    @Select("""
            SELECT operator_user_id AS operatorUserId,
                   operation_type AS operationType,
                   idempotency_key AS idempotencyKey,
                   order_no AS orderNo
            FROM o_order_operate_idempotency
            WHERE operator_user_id = #{operatorUserId}
              AND operation_type = #{operationType}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    OrderOperateIdempotencyRecord findOperateIdempotency(@Param("operatorUserId") Long operatorUserId,
                                                         @Param("operationType") String operationType,
                                                         @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT id,
                   order_no AS orderNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   status,
                   total_amount_cent AS totalAmountCent,
                   item_count AS itemCount,
                   payment_no AS paymentNo,
                   cancel_reason AS cancelReason,
                   timeout_at AS timeoutAt,
                   paid_at AS paidAt,
                   biz_source AS bizSource,
                   chat_conversation_id AS chatConversationId,
                   chat_listing_id AS chatListingId,
                   is_deleted AS isDeleted,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order
            WHERE order_no = #{orderNo}
            LIMIT 1
            """)
    OrderRecord findOrderByOrderNo(@Param("orderNo") String orderNo);

    @Select("""
            SELECT payment_mode
            FROM o_order
            WHERE order_no = #{orderNo}
            LIMIT 1
            """)
    String findPaymentModeByOrderNo(@Param("orderNo") String orderNo);

    @Select("""
            SELECT id,
                   order_no AS orderNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   status,
                   total_amount_cent AS totalAmountCent,
                   item_count AS itemCount,
                   payment_no AS paymentNo,
                   cancel_reason AS cancelReason,
                   timeout_at AS timeoutAt,
                   paid_at AS paidAt,
                   biz_source AS bizSource,
                   chat_conversation_id AS chatConversationId,
                   chat_listing_id AS chatListingId,
                   is_deleted AS isDeleted,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order
            WHERE payment_no = #{paymentNo}
            LIMIT 1
            """)
    OrderRecord findOrderByPaymentNo(@Param("paymentNo") String paymentNo);

    @Select("""
            SELECT id,
                   order_id AS orderId,
                   order_no AS orderNo,
                   product_id AS productId,
                   product_no AS productNo,
                   sku_id AS skuId,
                   sku_no AS skuNo,
                   sku_name AS skuName,
                   spec_json AS specJson,
                   price_cent AS priceCent,
                   quantity,
                   subtotal_cent AS subtotalCent,
                   owner_user_id AS ownerUserId
            FROM o_order_item
            WHERE order_no = #{orderNo}
            ORDER BY id ASC
            """)
    List<OrderItemRecord> listOrderItemsByOrderNo(@Param("orderNo") String orderNo);

    @Select("""
            SELECT COUNT(1)
            FROM o_order
            WHERE buyer_user_id = #{buyerUserId}
              AND is_deleted = 0
            """)
    long countOrdersByBuyer(@Param("buyerUserId") Long buyerUserId);

    @Select("""
            SELECT id,
                   order_no AS orderNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   status,
                   total_amount_cent AS totalAmountCent,
                   item_count AS itemCount,
                   payment_no AS paymentNo,
                   cancel_reason AS cancelReason,
                   timeout_at AS timeoutAt,
                   paid_at AS paidAt,
                   biz_source AS bizSource,
                   chat_conversation_id AS chatConversationId,
                   chat_listing_id AS chatListingId,
                   is_deleted AS isDeleted,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order
            WHERE buyer_user_id = #{buyerUserId}
              AND is_deleted = 0
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<OrderRecord> listOrdersByBuyer(@Param("buyerUserId") Long buyerUserId,
                                        @Param("size") int size,
                                        @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM o_order
            WHERE seller_user_id = #{sellerUserId}
              AND is_deleted = 0
            <if test="orderNo != null and orderNo != ''">
              AND order_no = #{orderNo}
            </if>
            <if test="status != null">
              AND status = #{status}
            </if>
            <if test="createdFrom != null">
              AND created_at <![CDATA[ >= ]]> #{createdFrom}
            </if>
            <if test="createdTo != null">
              AND created_at <![CDATA[ <= ]]> #{createdTo}
            </if>
            </script>
            """)
    long countOrdersBySeller(@Param("sellerUserId") Long sellerUserId,
                             @Param("orderNo") String orderNo,
                             @Param("status") Integer status,
                             @Param("createdFrom") LocalDateTime createdFrom,
                             @Param("createdTo") LocalDateTime createdTo);

    @Select("""
            <script>
            SELECT id,
                   order_no AS orderNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   status,
                   total_amount_cent AS totalAmountCent,
                   item_count AS itemCount,
                   payment_no AS paymentNo,
                   cancel_reason AS cancelReason,
                   timeout_at AS timeoutAt,
                   paid_at AS paidAt,
                   biz_source AS bizSource,
                   chat_conversation_id AS chatConversationId,
                   chat_listing_id AS chatListingId,
                   is_deleted AS isDeleted,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order
            WHERE seller_user_id = #{sellerUserId}
              AND is_deleted = 0
            <if test="orderNo != null and orderNo != ''">
              AND order_no = #{orderNo}
            </if>
            <if test="status != null">
              AND status = #{status}
            </if>
            <if test="createdFrom != null">
              AND created_at <![CDATA[ >= ]]> #{createdFrom}
            </if>
            <if test="createdTo != null">
              AND created_at <![CDATA[ <= ]]> #{createdTo}
            </if>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<OrderRecord> listOrdersBySeller(@Param("sellerUserId") Long sellerUserId,
                                         @Param("orderNo") String orderNo,
                                         @Param("status") Integer status,
                                         @Param("createdFrom") LocalDateTime createdFrom,
                                         @Param("createdTo") LocalDateTime createdTo,
                                         @Param("size") int size,
                                         @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM o_order
            WHERE is_deleted = 0
            <if test="orderNo != null and orderNo != ''">
              AND order_no = #{orderNo}
            </if>
            <if test="status != null">
              AND status = #{status}
            </if>
            <if test="buyerUserId != null">
              AND buyer_user_id = #{buyerUserId}
            </if>
            <if test="sellerUserId != null">
              AND seller_user_id = #{sellerUserId}
            </if>
            </script>
            """)
    long countOrdersForAdmin(@Param("orderNo") String orderNo,
                             @Param("status") Integer status,
                             @Param("buyerUserId") Long buyerUserId,
                             @Param("sellerUserId") Long sellerUserId);

    @Select("""
            <script>
            SELECT id,
                   order_no AS orderNo,
                   buyer_user_id AS buyerUserId,
                   seller_user_id AS sellerUserId,
                   status,
                   total_amount_cent AS totalAmountCent,
                   item_count AS itemCount,
                   payment_no AS paymentNo,
                   cancel_reason AS cancelReason,
                   timeout_at AS timeoutAt,
                   paid_at AS paidAt,
                   biz_source AS bizSource,
                   chat_conversation_id AS chatConversationId,
                   chat_listing_id AS chatListingId,
                   is_deleted AS isDeleted,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order
            WHERE is_deleted = 0
            <if test="orderNo != null and orderNo != ''">
              AND order_no = #{orderNo}
            </if>
            <if test="status != null">
              AND status = #{status}
            </if>
            <if test="buyerUserId != null">
              AND buyer_user_id = #{buyerUserId}
            </if>
            <if test="sellerUserId != null">
              AND seller_user_id = #{sellerUserId}
            </if>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<OrderRecord> listOrdersForAdmin(@Param("orderNo") String orderNo,
                                         @Param("status") Integer status,
                                         @Param("buyerUserId") Long buyerUserId,
                                         @Param("sellerUserId") Long sellerUserId,
                                         @Param("size") int size,
                                         @Param("offset") int offset);

    @Update("""
            UPDATE o_order
            SET status = #{paidStatus},
                payment_no = #{paymentNo},
                paid_at = #{paidAt},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE order_no = #{orderNo}
              AND buyer_user_id = #{buyerUserId}
              AND status = #{expectStatus}
              AND is_deleted = 0
            """)
    int markOrderPaid(@Param("orderNo") String orderNo,
                      @Param("buyerUserId") Long buyerUserId,
                      @Param("paymentNo") String paymentNo,
                      @Param("paidAt") LocalDateTime paidAt,
                      @Param("expectStatus") Integer expectStatus,
                      @Param("paidStatus") Integer paidStatus);

    @Update("""
            UPDATE o_order
            SET status = #{paidStatus},
                payment_no = #{paymentNo},
                payment_mode = 'BALANCE_ESCROW',
                paid_at = #{paidAt},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE order_no = #{orderNo}
              AND buyer_user_id = #{buyerUserId}
              AND status = #{expectStatus}
              AND is_deleted = 0
            """)
    int markOrderPaidByBalance(@Param("orderNo") String orderNo,
                               @Param("buyerUserId") Long buyerUserId,
                               @Param("paymentNo") String paymentNo,
                               @Param("paidAt") LocalDateTime paidAt,
                               @Param("expectStatus") Integer expectStatus,
                               @Param("paidStatus") Integer paidStatus);

    @Update("""
            UPDATE o_order
            SET status = #{canceledStatus},
                cancel_reason = #{reason},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE order_no = #{orderNo}
              AND buyer_user_id = #{buyerUserId}
              AND status = #{expectStatus}
              AND is_deleted = 0
            """)
    int cancelOrderAsBuyer(@Param("orderNo") String orderNo,
                           @Param("buyerUserId") Long buyerUserId,
                           @Param("reason") String reason,
                           @Param("expectStatus") Integer expectStatus,
                           @Param("canceledStatus") Integer canceledStatus);

    @Update("""
            UPDATE o_order
            SET status = #{canceledStatus},
                cancel_reason = #{reason},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE order_no = #{orderNo}
              AND status = #{expectStatus}
              AND is_deleted = 0
            """)
    int cancelOrderByTimeout(@Param("orderNo") String orderNo,
                             @Param("reason") String reason,
                             @Param("expectStatus") Integer expectStatus,
                             @Param("canceledStatus") Integer canceledStatus);

    @Update("""
            UPDATE o_order
            SET status = #{deliveringStatus},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE order_no = #{orderNo}
              AND seller_user_id = #{sellerUserId}
              AND status = #{expectStatus}
              AND is_deleted = 0
            """)
    int markOrderDeliveringBySeller(@Param("orderNo") String orderNo,
                                    @Param("sellerUserId") Long sellerUserId,
                                    @Param("expectStatus") Integer expectStatus,
                                    @Param("deliveringStatus") Integer deliveringStatus);

    @Update("""
            UPDATE o_order
            SET status = #{deliveringStatus},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE order_no = #{orderNo}
              AND status = #{expectStatus}
              AND is_deleted = 0
            """)
    int markOrderDeliveringAsAdmin(@Param("orderNo") String orderNo,
                                   @Param("expectStatus") Integer expectStatus,
                                   @Param("deliveringStatus") Integer deliveringStatus);

    @Update("""
            UPDATE o_order
            SET status = #{finishedStatus},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE order_no = #{orderNo}
              AND seller_user_id = #{sellerUserId}
              AND status = #{expectStatus}
              AND is_deleted = 0
            """)
    int markOrderFinishedBySeller(@Param("orderNo") String orderNo,
                                  @Param("sellerUserId") Long sellerUserId,
                                  @Param("expectStatus") Integer expectStatus,
                                  @Param("finishedStatus") Integer finishedStatus);

    @Update("""
            UPDATE o_order
            SET status = #{finishedStatus},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE order_no = #{orderNo}
              AND status = #{expectStatus}
              AND is_deleted = 0
            """)
    int markOrderFinishedAsAdmin(@Param("orderNo") String orderNo,
                                 @Param("expectStatus") Integer expectStatus,
                                 @Param("finishedStatus") Integer finishedStatus);

    @Update("""
            UPDATE o_order
            SET status = #{finishedStatus},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE order_no = #{orderNo}
              AND buyer_user_id = #{buyerUserId}
              AND status = #{expectStatus}
              AND is_deleted = 0
            """)
    int markOrderFinishedByBuyer(@Param("orderNo") String orderNo,
                                 @Param("buyerUserId") Long buyerUserId,
                                 @Param("expectStatus") Integer expectStatus,
                                 @Param("finishedStatus") Integer finishedStatus);

    @Insert("""
            INSERT INTO o_outbox_event (
                event_id,
                aggregate_id,
                type,
                payload,
                exchange_name,
                routing_key,
                status,
                retry_count,
                last_error,
                next_retry_at,
                created_at,
                sent_at
            ) VALUES (
                #{eventId},
                #{aggregateId},
                #{type},
                #{payload},
                #{exchangeName},
                #{routingKey},
                #{status},
                #{retryCount},
                #{lastError},
                #{nextRetryAt},
                CURRENT_TIMESTAMP(3),
                #{sentAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertOutboxEvent(OutboxEventEntity outboxEventEntity);

    @Select("""
            SELECT id,
                   event_id AS eventId,
                   aggregate_id AS aggregateId,
                   type,
                   payload,
                   exchange_name AS exchangeName,
                   routing_key AS routingKey,
                   status,
                   retry_count AS retryCount,
                   last_error AS lastError,
                   next_retry_at AS nextRetryAt,
                   created_at AS createdAt,
                   sent_at AS sentAt
            FROM o_outbox_event
            WHERE status = 'PENDING'
               OR (status = 'FAILED'
                   AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP(3)))
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<OutboxEventRecord> listRelayCandidates(@Param("limit") int limit);

    @Update("""
            UPDATE o_outbox_event
            SET status = 'SENT',
                sent_at = CURRENT_TIMESTAMP(3),
                last_error = NULL,
                next_retry_at = NULL
            WHERE id = #{id}
              AND status IN ('PENDING', 'FAILED')
            """)
    int markOutboxSent(@Param("id") Long id);

    @Update("""
            UPDATE o_outbox_event
            SET status = 'FAILED',
                retry_count = #{retryCount},
                last_error = #{lastError},
                next_retry_at = #{nextRetryAt}
            WHERE id = #{id}
            """)
    int markOutboxFailed(@Param("id") Long id,
                         @Param("retryCount") int retryCount,
                         @Param("lastError") String lastError,
                         @Param("nextRetryAt") LocalDateTime nextRetryAt);

    @Insert("""
            INSERT INTO o_admin_audit_log (
                operator_user_id,
                target_order_no,
                action,
                before_json,
                after_json,
                reason,
                created_at
            ) VALUES (
                #{operatorUserId},
                #{targetOrderNo},
                #{action},
                #{beforeJson},
                #{afterJson},
                #{reason},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertAdminAuditLog(@Param("operatorUserId") Long operatorUserId,
                            @Param("targetOrderNo") String targetOrderNo,
                            @Param("action") String action,
                            @Param("beforeJson") String beforeJson,
                            @Param("afterJson") String afterJson,
                            @Param("reason") String reason);

    @Insert("""
            INSERT INTO o_order_status_audit_log (
                target_order_no,
                operator_user_id,
                source,
                from_status,
                to_status,
                reason,
                created_at
            ) VALUES (
                #{targetOrderNo},
                #{operatorUserId},
                #{source},
                #{fromStatus},
                #{toStatus},
                #{reason},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertStatusAuditLog(@Param("targetOrderNo") String targetOrderNo,
                             @Param("operatorUserId") Long operatorUserId,
                             @Param("source") String source,
                             @Param("fromStatus") Integer fromStatus,
                             @Param("toStatus") Integer toStatus,
                             @Param("reason") String reason);

    @Select("""
            SELECT COUNT(1)
            FROM o_order_status_audit_log
            WHERE target_order_no = #{orderNo}
            """)
    long countStatusAuditByOrderNo(@Param("orderNo") String orderNo);

    @Select("""
            SELECT id,
                   target_order_no AS orderNo,
                   operator_user_id AS operatorUserId,
                   source,
                   from_status AS fromStatus,
                   to_status AS toStatus,
                   reason,
                   created_at AS createdAt
            FROM o_order_status_audit_log
            WHERE target_order_no = #{orderNo}
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<OrderStatusAuditRecord> listStatusAuditByOrderNo(@Param("orderNo") String orderNo,
                                                          @Param("size") int size,
                                                          @Param("offset") int offset);

    @Select("""
            SELECT id,
                   target_order_no AS orderNo,
                   operator_user_id AS operatorUserId,
                   source,
                   from_status AS fromStatus,
                   to_status AS toStatus,
                   reason,
                   created_at AS createdAt
            FROM o_order_status_audit_log
            WHERE target_order_no = #{orderNo}
            ORDER BY id ASC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<OrderStatusAuditRecord> listStatusAuditTimelineByOrderNo(@Param("orderNo") String orderNo,
                                                                   @Param("size") int size,
                                                                   @Param("offset") int offset);
}
