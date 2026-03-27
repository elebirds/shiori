package moe.hhm.shiori.search.mq;

import moe.hhm.shiori.search.event.ProductSearchRemovedPayload;
import moe.hhm.shiori.search.event.ProductSearchUpsertedPayload;
import moe.hhm.shiori.search.model.ProductSearchDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchIndexingServiceTest {

    @Mock
    private ProductSearchIndexRepository repository;

    @Test
    void shouldIgnoreOlderVersionWhenUpserting() {
        when(repository.findIndexedVersion("1001")).thenReturn(9L);
        ProductSearchIndexingService service = new ProductSearchIndexingService(repository);

        service.handleUpsert("event-1", new ProductSearchUpsertedPayload(
                1001L,
                "P001",
                2002L,
                "Java Book",
                "desc",
                "product/2002/202603/a.jpg",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main",
                3900L,
                4900L,
                8,
                2,
                8L,
                "2026-03-27T00:00:00",
                "2026-03-27T00:01:00"
        ), new KafkaMessageMetadata("topic-a", 0, 1L, "group-a"));

        verify(repository, never()).upsert(anyString(), org.mockito.ArgumentMatchers.any(ProductSearchDocument.class));
    }

    @Test
    void shouldUpsertWhenVersionIsNewerOrEqual() {
        when(repository.findIndexedVersion("1001")).thenReturn(8L);
        ProductSearchIndexingService service = new ProductSearchIndexingService(repository);

        service.handleUpsert("event-2", new ProductSearchUpsertedPayload(
                1001L,
                "P001",
                2002L,
                "Java Book",
                "desc",
                "product/2002/202603/a.jpg",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main",
                3900L,
                4900L,
                8,
                2,
                9L,
                "2026-03-27T00:00:00",
                "2026-03-27T00:01:00"
        ), new KafkaMessageMetadata("topic-a", 0, 2L, "group-a"));

        ArgumentCaptor<ProductSearchDocument> documentCaptor = ArgumentCaptor.forClass(ProductSearchDocument.class);
        verify(repository).upsert(eq("1001"), documentCaptor.capture());
        assertThat(documentCaptor.getValue().version()).isEqualTo(9L);
        assertThat(documentCaptor.getValue().title()).isEqualTo("Java Book");
    }

    @Test
    void shouldDeleteWhenRemoveVersionIsNewerOrEqual() {
        when(repository.findIndexedVersion("1001")).thenReturn(8L);
        ProductSearchIndexingService service = new ProductSearchIndexingService(repository);

        service.handleRemove("event-3", new ProductSearchRemovedPayload(
                1001L,
                "P001",
                3,
                9L,
                "2026-03-27T00:02:00",
                "OFF_SHELF"
        ), new KafkaMessageMetadata("topic-a", 0, 3L, "group-a"));

        verify(repository).delete("1001");
    }
}
