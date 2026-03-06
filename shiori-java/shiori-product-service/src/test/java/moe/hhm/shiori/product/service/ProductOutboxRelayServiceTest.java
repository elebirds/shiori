package moe.hhm.shiori.product.service;

import java.util.List;
import moe.hhm.shiori.product.config.ProductOutboxProperties;
import moe.hhm.shiori.product.model.ProductOutboxEventRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOutboxRelayServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductEventPublisher productEventPublisher;

    private ProductOutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        relayService = new ProductOutboxRelayService(
                productMapper,
                productEventPublisher,
                new ProductOutboxProperties()
        );
    }

    @Test
    void shouldMarkSentWhenPublishSuccess() {
        ProductOutboxEventRecord event = new ProductOutboxEventRecord(
                1L, "event-1", "P001", "PRODUCT_PUBLISHED",
                "{\"eventId\":\"event-1\"}", "shiori.product.event", "product.published",
                "PENDING", 0, null, null, null, null
        );
        when(productMapper.listProductOutboxRelayCandidates(100)).thenReturn(List.of(event));

        relayService.relayPendingEvents();

        verify(productEventPublisher).publishEnvelope("shiori.product.event", "product.published", "{\"eventId\":\"event-1\"}");
        verify(productMapper).markProductOutboxSent(1L);
    }

    @Test
    void shouldMarkFailedWhenPublishThrows() {
        ProductOutboxEventRecord event = new ProductOutboxEventRecord(
                2L, "event-2", "P002", "PRODUCT_PUBLISHED",
                "{\"eventId\":\"event-2\"}", "shiori.product.event", "product.published",
                "FAILED", 1, "last", null, null, null
        );
        when(productMapper.listProductOutboxRelayCandidates(100)).thenReturn(List.of(event));
        doThrow(new RuntimeException("publish failed")).when(productEventPublisher)
                .publishEnvelope("shiori.product.event", "product.published", "{\"eventId\":\"event-2\"}");

        relayService.relayPendingEvents();

        verify(productMapper).markProductOutboxFailed(eq(2L), eq(2), eq("publish failed"), any());
    }
}
