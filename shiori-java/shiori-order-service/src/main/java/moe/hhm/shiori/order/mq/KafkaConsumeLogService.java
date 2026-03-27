package moe.hhm.shiori.order.mq;

import moe.hhm.shiori.order.model.KafkaConsumeLogEntity;
import moe.hhm.shiori.order.model.KafkaConsumeLogRecord;
import moe.hhm.shiori.order.repository.KafkaConsumeLogMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    public KafkaConsumeLogService(KafkaConsumeLogMapper kafkaConsumeLogMapper) {
        this.kafkaConsumeLogMapper = kafkaConsumeLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean startProcessing(String eventId, String eventType, KafkaMessageMetadata metadata) {
        KafkaConsumeLogRecord existed = kafkaConsumeLogMapper.findByConsumerGroupAndEventId(
                metadata.consumerGroup(),
                eventId
        );
        if (existed != null && STATUS_SUCCEEDED.equalsIgnoreCase(existed.status())) {
            return false;
        }

        KafkaConsumeLogEntity entity = toEntity(eventId, eventType, metadata);
        entity.setStatus(STATUS_PROCESSING);
        entity.setLastError(null);
        kafkaConsumeLogMapper.upsert(entity);
        return true;
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
