package moe.hhm.shiori.social.mq;

import moe.hhm.shiori.social.event.ProductPublishedPayload;
import moe.hhm.shiori.social.service.SocialPostService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "social.kafka", name = "enabled", havingValue = "true")
public class ProductOutboxCdcConsumeService {

    private final KafkaConsumeLogService kafkaConsumeLogService;
    private final SocialPostService socialPostService;

    public ProductOutboxCdcConsumeService(KafkaConsumeLogService kafkaConsumeLogService,
                                          SocialPostService socialPostService) {
        this.kafkaConsumeLogService = kafkaConsumeLogService;
        this.socialPostService = socialPostService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(String eventId, ProductPublishedPayload payload, KafkaMessageMetadata metadata) {
        try {
            if (!kafkaConsumeLogService.startProcessing(eventId, "PRODUCT_PUBLISHED", metadata)) {
                return;
            }
            socialPostService.createAutoPostFromProductPublished(payload);
            kafkaConsumeLogService.markSucceeded(eventId, metadata);
        } catch (KafkaProcessingInProgressException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            kafkaConsumeLogService.markFailed(eventId, metadata, ex);
            throw ex;
        }
    }
}
