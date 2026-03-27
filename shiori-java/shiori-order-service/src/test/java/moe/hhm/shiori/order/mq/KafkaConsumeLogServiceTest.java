package moe.hhm.shiori.order.mq;

import java.time.LocalDateTime;
import moe.hhm.shiori.order.model.KafkaConsumeLogRecord;
import moe.hhm.shiori.order.repository.KafkaConsumeLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaConsumeLogServiceTest {

    @Mock
    private KafkaConsumeLogMapper kafkaConsumeLogMapper;

    private KafkaConsumeLogService service;

    @BeforeEach
    void setUp() {
        service = new KafkaConsumeLogService(kafkaConsumeLogMapper, 300_000L);
    }

    @Test
    void shouldInsertProcessingLogWhenEventFirstConsumed() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("topic-a", 1, 10L, "group-a");
        when(kafkaConsumeLogMapper.findByConsumerGroupAndEventIdForUpdate("group-a", "event-1")).thenReturn(null);

        boolean claimed = service.startProcessing("event-1", "WalletBalanceChanged", metadata);

        assertThat(claimed).isTrue();
        verify(kafkaConsumeLogMapper).insertProcessing(any());
        verify(kafkaConsumeLogMapper, never()).updateProcessing(any());
    }

    @Test
    void shouldSkipWhenSucceededLogAlreadyExists() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("topic-a", 1, 10L, "group-a");
        when(kafkaConsumeLogMapper.findByConsumerGroupAndEventIdForUpdate("group-a", "event-2"))
                .thenReturn(new KafkaConsumeLogRecord("group-a", "event-2", "SUCCEEDED", LocalDateTime.now()));

        boolean claimed = service.startProcessing("event-2", "WalletBalanceChanged", metadata);

        assertThat(claimed).isFalse();
        verify(kafkaConsumeLogMapper, never()).insertProcessing(any());
        verify(kafkaConsumeLogMapper, never()).updateProcessing(any());
    }

    @Test
    void shouldDeferWhenRecentProcessingLogAlreadyExists() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("topic-a", 1, 10L, "group-a");
        when(kafkaConsumeLogMapper.findByConsumerGroupAndEventIdForUpdate("group-a", "event-3"))
                .thenReturn(new KafkaConsumeLogRecord("group-a", "event-3", "PROCESSING", LocalDateTime.now().minusSeconds(5)));

        assertThatThrownBy(() -> service.startProcessing("event-3", "WalletBalanceChanged", metadata))
                .isInstanceOf(KafkaProcessingInProgressException.class);
        verify(kafkaConsumeLogMapper, never()).insertProcessing(any());
        verify(kafkaConsumeLogMapper, never()).updateProcessing(any());
    }

    @Test
    void shouldReclaimFailedLogForRetry() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("topic-a", 1, 10L, "group-a");
        when(kafkaConsumeLogMapper.findByConsumerGroupAndEventIdForUpdate("group-a", "event-4"))
                .thenReturn(new KafkaConsumeLogRecord("group-a", "event-4", "FAILED", LocalDateTime.now()));

        boolean claimed = service.startProcessing("event-4", "WalletBalanceChanged", metadata);

        assertThat(claimed).isTrue();
        verify(kafkaConsumeLogMapper, never()).insertProcessing(any());
        verify(kafkaConsumeLogMapper).updateProcessing(any());
    }

    @Test
    void shouldReclaimExpiredProcessingLog() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("topic-a", 1, 10L, "group-a");
        when(kafkaConsumeLogMapper.findByConsumerGroupAndEventIdForUpdate("group-a", "event-5"))
                .thenReturn(new KafkaConsumeLogRecord("group-a", "event-5", "PROCESSING", LocalDateTime.now().minusMinutes(10)));

        boolean claimed = service.startProcessing("event-5", "WalletBalanceChanged", metadata);

        assertThat(claimed).isTrue();
        verify(kafkaConsumeLogMapper, never()).insertProcessing(any());
        verify(kafkaConsumeLogMapper).updateProcessing(any());
    }

    @Test
    void shouldDeferWhenConcurrentInsertLeavesRecentProcessingLog() {
        KafkaMessageMetadata metadata = new KafkaMessageMetadata("topic-a", 1, 10L, "group-a");
        when(kafkaConsumeLogMapper.findByConsumerGroupAndEventIdForUpdate("group-a", "event-6"))
                .thenReturn(null)
                .thenReturn(new KafkaConsumeLogRecord("group-a", "event-6", "PROCESSING", LocalDateTime.now()));
        when(kafkaConsumeLogMapper.insertProcessing(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.startProcessing("event-6", "WalletBalanceChanged", metadata))
                .isInstanceOf(KafkaProcessingInProgressException.class);
        verify(kafkaConsumeLogMapper).insertProcessing(any());
        verify(kafkaConsumeLogMapper, never()).updateProcessing(any());
    }
}
