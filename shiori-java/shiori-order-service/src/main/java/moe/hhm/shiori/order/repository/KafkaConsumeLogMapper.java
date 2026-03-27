package moe.hhm.shiori.order.repository;

import moe.hhm.shiori.order.model.KafkaConsumeLogEntity;
import moe.hhm.shiori.order.model.KafkaConsumeLogRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KafkaConsumeLogMapper {

    @Select("""
            SELECT consumer_group AS consumerGroup,
                   event_id AS eventId,
                   status,
                   updated_at AS updatedAt
            FROM o_event_consume_log
            WHERE consumer_group = #{consumerGroup}
              AND event_id = #{eventId}
            LIMIT 1
            FOR UPDATE
            """)
    KafkaConsumeLogRecord findByConsumerGroupAndEventIdForUpdate(@Param("consumerGroup") String consumerGroup,
                                                                 @Param("eventId") String eventId);

    @Insert("""
            INSERT INTO o_event_consume_log (
                consumer_group,
                event_id,
                event_type,
                topic,
                partition_id,
                message_offset,
                status,
                last_error,
                created_at,
                updated_at
            ) VALUES (
                #{consumerGroup},
                #{eventId},
                #{eventType},
                #{topic},
                #{partitionId},
                #{messageOffset},
                #{status},
                #{lastError},
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertProcessing(KafkaConsumeLogEntity entity);

    @Update("""
            UPDATE o_event_consume_log
            SET event_type = #{eventType},
                topic = #{topic},
                partition_id = #{partitionId},
                message_offset = #{messageOffset},
                status = #{status},
                last_error = NULL,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE consumer_group = #{consumerGroup}
              AND event_id = #{eventId}
              AND status <> 'SUCCEEDED'
            """)
    int updateProcessing(KafkaConsumeLogEntity entity);

    @Update("""
            UPDATE o_event_consume_log
            SET topic = #{topic},
                partition_id = #{partitionId},
                message_offset = #{messageOffset},
                status = 'SUCCEEDED',
                last_error = NULL,
                processed_at = CURRENT_TIMESTAMP(3),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE consumer_group = #{consumerGroup}
              AND event_id = #{eventId}
            """)
    int markSucceeded(KafkaConsumeLogEntity entity);

    @Update("""
            UPDATE o_event_consume_log
            SET topic = #{topic},
                partition_id = #{partitionId},
                message_offset = #{messageOffset},
                status = 'FAILED',
                last_error = #{lastError},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE consumer_group = #{consumerGroup}
              AND event_id = #{eventId}
            """)
    int markFailed(KafkaConsumeLogEntity entity);
}
