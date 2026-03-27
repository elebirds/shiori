package moe.hhm.shiori.social.mq;

import moe.hhm.shiori.social.event.ProductPublishedPayload;
import moe.hhm.shiori.social.service.SocialPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.same;

@ExtendWith(MockitoExtension.class)
class ProductOutboxCdcConsumeServiceTest {

    @Mock
    private KafkaConsumeLogService consumeLogService;
    @Mock
    private SocialPostService socialPostService;

    private ProductOutboxCdcConsumeService service;

    @BeforeEach
    void setUp() {
        service = new ProductOutboxCdcConsumeService(consumeLogService, socialPostService);
    }

    @Test
    void shouldSkipDuplicateSucceededEvent() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("shiori.cdc.product.outbox.raw", 0, 2L, "group-a");
        ProductPublishedPayload payload = payload();
        when(consumeLogService.startProcessing("event-1", "PRODUCT_PUBLISHED", metadata)).thenReturn(false);

        service.handle("event-1", payload, metadata);

        verify(socialPostService, never()).createAutoPostFromProductPublished(any());
        verify(consumeLogService, never()).markSucceeded(any(), any());
        verify(consumeLogService, never()).markFailed(any(), any(), any());
    }

    @Test
    void shouldMarkSucceededAfterAutoPostCreated() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("shiori.cdc.product.outbox.raw", 0, 3L, "group-a");
        ProductPublishedPayload payload = payload();
        when(consumeLogService.startProcessing("event-2", "PRODUCT_PUBLISHED", metadata)).thenReturn(true);

        service.handle("event-2", payload, metadata);

        verify(socialPostService).createAutoPostFromProductPublished(payload);
        verify(consumeLogService).markSucceeded("event-2", metadata);
    }

    @Test
    void shouldMarkFailedAndRethrowWhenAutoPostCreationFails() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("shiori.cdc.product.outbox.raw", 0, 4L, "group-a");
        ProductPublishedPayload payload = payload();
        when(consumeLogService.startProcessing("event-3", "PRODUCT_PUBLISHED", metadata)).thenReturn(true);
        doThrow(new IllegalStateException("insert failed"))
                .when(socialPostService)
                .createAutoPostFromProductPublished(payload);

        assertThatThrownBy(() -> service.handle("event-3", payload, metadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insert failed");

        verify(consumeLogService).markFailed(eq("event-3"), eq(metadata), any(IllegalStateException.class));
    }

    @Test
    void shouldWrapNonTransientDataAccessFailureAsNonRetryable() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("shiori.cdc.product.outbox.raw", 0, 4L, "group-a");
        ProductPublishedPayload payload = payload();
        DataIntegrityViolationException cause = new DataIntegrityViolationException("data too long");
        when(consumeLogService.startProcessing("event-3a", "PRODUCT_PUBLISHED", metadata)).thenReturn(true);
        doThrow(cause)
                .when(socialPostService)
                .createAutoPostFromProductPublished(payload);

        assertThatThrownBy(() -> service.handle("event-3a", payload, metadata))
                .isInstanceOf(NonRetryableKafkaConsumerException.class)
                .hasCause(cause);

        verify(consumeLogService).markFailed("event-3a", metadata, cause);
    }

    @Test
    void shouldRethrowWithoutMarkFailedWhenProcessingStillOwnedByAnotherAttempt() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("shiori.cdc.product.outbox.raw", 0, 5L, "group-a");
        ProductPublishedPayload payload = payload();
        when(consumeLogService.startProcessing("event-4", "PRODUCT_PUBLISHED", metadata))
                .thenThrow(new KafkaProcessingInProgressException("event-4"));

        assertThatThrownBy(() -> service.handle("event-4", payload, metadata))
                .isInstanceOf(KafkaProcessingInProgressException.class);

        verify(socialPostService, never()).createAutoPostFromProductPublished(any());
        verify(consumeLogService, never()).markFailed(any(), any(), any());
        verify(consumeLogService, never()).markSucceeded(any(), any());
    }

    private ProductPublishedPayload payload() {
        return new ProductPublishedPayload(
                1001L,
                "P001",
                2002L,
                "Java Book",
                "product/2002/202603/a.jpg",
                3900L,
                4900L,
                "main"
        );
    }
}
