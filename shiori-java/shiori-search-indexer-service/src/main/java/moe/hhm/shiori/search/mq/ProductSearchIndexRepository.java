package moe.hhm.shiori.search.mq;

import moe.hhm.shiori.search.model.ProductSearchDocument;

public interface ProductSearchIndexRepository {

    Long findIndexedVersion(String documentId);

    void upsert(String documentId, ProductSearchDocument document);

    void delete(String documentId);
}
