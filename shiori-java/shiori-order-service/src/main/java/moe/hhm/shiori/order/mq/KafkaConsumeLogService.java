package moe.hhm.shiori.order.mq;

import java.time.LocalDateTime;
import moe.hhm.shiori.order.model.KafkaConsumeLogEntity;
import moe.hhm.shiori.order.model.KafkaConsumeLogRecord;
import moe.hhm.shiori.order.repository.KafkaConsumeLogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "order.kafka", name = "enabled", havingValue = "true")
public class KafkaConsumeLogService {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final int MAX_ERROR_LENGTH = 512;

    private final KafkaConsumeLogMapper kafkaConsumeLogMapper;
    private final long processingTimeoutMs;

    public KafkaConsumeLogService(KafkaConsumeLogMapper kafkaConsumeLogMapper,
                                  @Value("${order.kafka.processing-timeout-ms:300000}") long processingTimeoutMs) {
        this.kafkaConsumeLogMapper = kafkaConsumeLogMapper;
        this.processingTimeoutMs = Math.max(processingTimeoutMs, 0L);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean startProcessing(String eventId, String eventType, KafkaMessageMetadata metadata) {
        KafkaConsumeLogRecord existed = kafkaConsumeLogMapper.findByConsumerGroupAndEventIdForUpdate(
                metadata.consumerGroup(),
                eventId
        );
        KafkaConsumeLogEntity entity = toEntity(eventId, eventType, metadata);
        entity.setStatus(STATUS_PROCESSING);
        entity.setLastError(null);
        if (existed == null) {
            return insertProcessing(entity);
        }
        return claimExistingRecord(entity, existed);
    }

    public void markSucceeded(String eventId, KafkaMessageMetadata metadata) {
        kafkaConsumeLogMapper.markSucceeded(toEntity(eventId, null, metadata));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventId, KafkaMessageMetadata metadata, Throwable throwable) {
        KafkaConsumeLogEntity entity = toEntity(eventId, null, metadata);
        entity.setLastError(truncateError(throwable));
        kafkaConsumeLogMapper.markFailed(entity);
    }

    private KafkaConsumeLogEntity toEntity(String eventId, String eventType, KafkaMessageMetadata metadata) {
        KafkaConsumeLogEntity entity = new KafkaConsumeLogEntity();
        entity.setConsumerGroup(metadata.consumerGroup());
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setTopic(metadata.topic());
        entity.setPartitionId(metadata.partition());
        entity.setMessageOffset(metadata.offset());
        return entity;
    }

    private boolean insertProcessing(KafkaConsumeLogEntity entity) {
        try {
            kafkaConsumeLogMapper.insertProcessing(entity);
            return true;
        } catch (DuplicateKeyException ex) {
            KafkaConsumeLogRecord duplicated = kafkaConsumeLogMapper.findByConsumerGroupAndEventIdForUpdate(
                    entity.getConsumerGroup(),
                    entity.getEventId()
            );
            if (duplicated == null) {
                kafkaConsumeLogMapper.insertProcessing(entity);
                return true;
            }
            return claimExistingRecord(entity, duplicated);
        }
    }

    private boolean claimExistingRecord(KafkaConsumeLogEntity entity, KafkaConsumeLogRecord existed) {
        if (existed != null && STATUS_SUCCEEDED.equalsIgnoreCase(existed.status())) {
            return false;
        }
        if (isRecentProcessing(existed)) {
            throw new KafkaProcessingInProgressException(entity.getEventId());
        }
        kafkaConsumeLogMapper.updateProcessing(entity);
        return true;
    }

    private boolean isRecentProcessing(KafkaConsumeLogRecord existed) {
        if (existed == null || !STATUS_PROCESSING.equalsIgnoreCase(existed.status())) {
            return false;
        }
        if (existed.updatedAt() == null) {
            return false;
        }
        return existed.updatedAt().isAfter(LocalDateTime.now().minusNanos(processingTimeoutMs * 1_000_000L));
    }

    private String truncateError(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getClass().getSimpleName();
        if (StringUtils.hasText(throwable.getMessage())) {
            message = message + ": " + throwable.getMessage().trim();
        }
        if (message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
