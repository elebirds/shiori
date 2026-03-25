package moe.hhm.shiori.order.repository;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.order.model.OrderCommandEntity;
import moe.hhm.shiori.order.model.OrderCommandRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderCommandMapper {

    @Insert("""
            INSERT INTO o_order_command (
                command_no,
                command_type,
                operator_user_id,
                idempotency_key,
                order_no,
                status,
                request_payload,
                progress_payload,
                result_code,
                result_message,
                retry_count,
                last_error,
                next_retry_at,
                created_at,
                updated_at
            ) VALUES (
                #{commandNo},
                #{commandType},
                #{operatorUserId},
                #{idempotencyKey},
                #{orderNo},
                #{status},
                #{requestPayload},
                #{progressPayload},
                #{resultCode},
                #{resultMessage},
                #{retryCount},
                #{lastError},
                #{nextRetryAt},
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertOrderCommand(OrderCommandEntity entity);

    @Select("""
            SELECT id,
                   command_no AS commandNo,
                   command_type AS commandType,
                   operator_user_id AS operatorUserId,
                   idempotency_key AS idempotencyKey,
                   order_no AS orderNo,
                   status,
                   request_payload AS requestPayload,
                   progress_payload AS progressPayload,
                   result_code AS resultCode,
                   result_message AS resultMessage,
                   retry_count AS retryCount,
                   last_error AS lastError,
                   next_retry_at AS nextRetryAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order_command
            WHERE operator_user_id = #{operatorUserId}
              AND command_type = #{commandType}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    OrderCommandRecord findByOperatorAndTypeAndIdempotencyKey(@Param("operatorUserId") Long operatorUserId,
                                                              @Param("commandType") String commandType,
                                                              @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT id,
                   command_no AS commandNo,
                   command_type AS commandType,
                   operator_user_id AS operatorUserId,
                   idempotency_key AS idempotencyKey,
                   order_no AS orderNo,
                   status,
                   request_payload AS requestPayload,
                   progress_payload AS progressPayload,
                   result_code AS resultCode,
                   result_message AS resultMessage,
                   retry_count AS retryCount,
                   last_error AS lastError,
                   next_retry_at AS nextRetryAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order_command
            WHERE command_no = #{commandNo}
            LIMIT 1
            """)
    OrderCommandRecord findByCommandNo(@Param("commandNo") String commandNo);

    @Select("""
            SELECT id,
                   command_no AS commandNo,
                   command_type AS commandType,
                   operator_user_id AS operatorUserId,
                   idempotency_key AS idempotencyKey,
                   order_no AS orderNo,
                   status,
                   request_payload AS requestPayload,
                   progress_payload AS progressPayload,
                   result_code AS resultCode,
                   result_message AS resultMessage,
                   retry_count AS retryCount,
                   last_error AS lastError,
                   next_retry_at AS nextRetryAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order_command
            WHERE order_no = #{orderNo}
              AND command_type = #{commandType}
              AND status IN ('PREPARED', 'REMOTE_SUCCEEDED', 'COMPENSATING')
            ORDER BY id DESC
            LIMIT 1
            """)
    OrderCommandRecord findActiveByOrderNoAndType(@Param("orderNo") String orderNo,
                                                  @Param("commandType") String commandType);

    @Update("""
            UPDATE o_order_command
            SET progress_payload = #{progressPayload},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND status = 'PREPARED'
            """)
    int markPreparedProgress(@Param("id") Long id,
                             @Param("progressPayload") String progressPayload);

    @Update("""
            UPDATE o_order_command
            SET status = 'REMOTE_SUCCEEDED',
                progress_payload = #{progressPayload},
                last_error = NULL,
                next_retry_at = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND status IN ('PREPARED', 'REMOTE_SUCCEEDED')
            """)
    int markRemoteSucceeded(@Param("id") Long id,
                            @Param("progressPayload") String progressPayload);

    @Update("""
            UPDATE o_order_command
            SET status = 'COMPLETED',
                result_code = #{resultCode},
                result_message = #{resultMessage},
                last_error = NULL,
                next_retry_at = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND status IN ('PREPARED', 'REMOTE_SUCCEEDED', 'COMPENSATING')
            """)
    int markCompleted(@Param("id") Long id,
                      @Param("resultCode") Integer resultCode,
                      @Param("resultMessage") String resultMessage);

    @Update("""
            UPDATE o_order_command
            SET status = 'FAILED',
                result_code = #{resultCode},
                result_message = #{resultMessage},
                last_error = #{lastError},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int markFailed(@Param("id") Long id,
                   @Param("resultCode") Integer resultCode,
                   @Param("resultMessage") String resultMessage,
                   @Param("lastError") String lastError);

    @Select("""
            SELECT id,
                   command_no AS commandNo,
                   command_type AS commandType,
                   operator_user_id AS operatorUserId,
                   idempotency_key AS idempotencyKey,
                   order_no AS orderNo,
                   status,
                   request_payload AS requestPayload,
                   progress_payload AS progressPayload,
                   result_code AS resultCode,
                   result_message AS resultMessage,
                   retry_count AS retryCount,
                   last_error AS lastError,
                   next_retry_at AS nextRetryAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order_command
            WHERE ((status = 'PREPARED' AND updated_at <= #{preparedBefore})
                OR (status IN ('REMOTE_SUCCEEDED', 'COMPENSATING')
                    AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP(3))))
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<OrderCommandRecord> listRecoveryCandidates(@Param("preparedBefore") LocalDateTime preparedBefore,
                                                    @Param("limit") int limit);

    @Update("""
            UPDATE o_order_command
            SET status = 'COMPENSATING',
                progress_payload = #{progressPayload},
                result_code = #{resultCode},
                result_message = #{resultMessage},
                retry_count = #{retryCount},
                last_error = #{lastError},
                next_retry_at = #{nextRetryAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int markCompensating(@Param("id") Long id,
                         @Param("progressPayload") String progressPayload,
                         @Param("resultCode") Integer resultCode,
                         @Param("resultMessage") String resultMessage,
                         @Param("retryCount") Integer retryCount,
                         @Param("lastError") String lastError,
                         @Param("nextRetryAt") LocalDateTime nextRetryAt);

    @Update("""
            UPDATE o_order_command
            SET status = 'COMPENSATED',
                progress_payload = #{progressPayload},
                result_code = #{resultCode},
                result_message = #{resultMessage},
                last_error = NULL,
                next_retry_at = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int markCompensated(@Param("id") Long id,
                        @Param("progressPayload") String progressPayload,
                        @Param("resultCode") Integer resultCode,
                        @Param("resultMessage") String resultMessage);

    @Update("""
            UPDATE o_order_command
            SET retry_count = #{retryCount},
                last_error = #{lastError},
                next_retry_at = #{nextRetryAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND status IN ('PREPARED', 'REMOTE_SUCCEEDED', 'COMPENSATING')
            """)
    int scheduleRetry(@Param("id") Long id,
                      @Param("retryCount") Integer retryCount,
                      @Param("lastError") String lastError,
                      @Param("nextRetryAt") LocalDateTime nextRetryAt);
}
