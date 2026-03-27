package moe.hhm.shiori.search.mq;

import moe.hhm.shiori.search.event.ProductSearchRemovedPayload;
import moe.hhm.shiori.search.event.ProductSearchUpsertedPayload;
import moe.hhm.shiori.search.model.ProductSearchDocument;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchIndexingService {

    private final ProductSearchIndexRepository repository;

    public ProductSearchIndexingService(ProductSearchIndexRepository repository) {
        this.repository = repository;
    }

    public void handleUpsert(String eventId, ProductSearchUpsertedPayload payload, KafkaMessageMetadata metadata) {
        if (payload == null || payload.productId() == null || payload.version() == null) {
            throw new NonRetryableKafkaConsumerException("search upsert payload is invalid");
        }
        String documentId = payload.productId().toString();
        Long indexedVersion = repository.findIndexedVersion(documentId);
        if (indexedVersion != null && payload.version() < indexedVersion) {
            return;
        }
        repository.upsert(documentId, new ProductSearchDocument(
                payload.productId(),
                payload.productNo(),
                payload.ownerUserId(),
                payload.title(),
                payload.description(),
                payload.coverObjectKey(),
                payload.categoryCode(),
                payload.subCategoryCode(),
                payload.conditionLevel(),
                payload.tradeMode(),
                payload.campusCode(),
                payload.minPriceCent(),
                payload.maxPriceCent(),
                payload.totalStock(),
                payload.status(),
                payload.version(),
                payload.createdAt(),
                payload.occurredAt()
        ));
    }

    public void handleRemove(String eventId, ProductSearchRemovedPayload payload, KafkaMessageMetadata metadata) {
        if (payload == null || payload.productId() == null || payload.version() == null) {
            throw new NonRetryableKafkaConsumerException("search remove payload is invalid");
        }
        String documentId = payload.productId().toString();
        Long indexedVersion = repository.findIndexedVersion(documentId);
        if (indexedVersion != null && payload.version() < indexedVersion) {
            return;
        }
        repository.delete(documentId);
    }
}
