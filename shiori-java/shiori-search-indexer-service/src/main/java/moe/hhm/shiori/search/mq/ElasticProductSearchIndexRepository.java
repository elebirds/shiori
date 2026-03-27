package moe.hhm.shiori.search.mq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import moe.hhm.shiori.search.model.ProductSearchDocument;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class ElasticProductSearchIndexRepository implements ProductSearchIndexRepository {

    private final RestClient restClient;
    private final String indexName;
    private final ObjectMapper objectMapper;
    private volatile boolean indexEnsured;

    public ElasticProductSearchIndexRepository(RestClient restClient,
                                               String indexName,
                                               ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.indexName = indexName;
        this.objectMapper = objectMapper;
    }

    @Override
    public Long findIndexedVersion(String documentId) {
        ensureIndex();
        Request request = new Request("GET", "/" + indexName + "/_doc/" + documentId);
        try {
            Response response = restClient.performRequest(request);
            JsonNode root = objectMapper.readTree(response.getEntity().getContent());
            JsonNode source = root.path("_source");
            if (source.isMissingNode() || source.path("version").isMissingNode()) {
                return null;
            }
            return source.path("version").asLong();
        } catch (ResponseException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatusLine().getStatusCode() == 404) {
                return null;
            }
            throw new IllegalStateException("query elasticsearch document failed", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("query elasticsearch document failed", ex);
        }
    }

    @Override
    public void upsert(String documentId, ProductSearchDocument document) {
        ensureIndex();
        Request request = new Request("PUT", "/" + indexName + "/_doc/" + documentId);
        try {
            request.setJsonEntity(objectMapper.writeValueAsString(document));
            restClient.performRequest(request);
        } catch (IOException ex) {
            throw new IllegalStateException("upsert elasticsearch document failed", ex);
        }
    }

    @Override
    public void delete(String documentId) {
        ensureIndex();
        Request request = new Request("DELETE", "/" + indexName + "/_doc/" + documentId);
        try {
            restClient.performRequest(request);
        } catch (ResponseException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatusLine().getStatusCode() == 404) {
                return;
            }
            throw new IllegalStateException("delete elasticsearch document failed", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("delete elasticsearch document failed", ex);
        }
    }

    private void ensureIndex() {
        if (indexEnsured) {
            return;
        }
        synchronized (this) {
            if (indexEnsured) {
                return;
            }
            Request getRequest = new Request("GET", "/" + indexName);
            try {
                restClient.performRequest(getRequest);
                indexEnsured = true;
                return;
            } catch (ResponseException ex) {
                if (ex.getResponse() == null || ex.getResponse().getStatusLine().getStatusCode() != 404) {
                    throw new IllegalStateException("check elasticsearch index failed", ex);
                }
            } catch (IOException ex) {
                throw new IllegalStateException("check elasticsearch index failed", ex);
            }
            Request createRequest = new Request("PUT", "/" + indexName);
            createRequest.setJsonEntity(indexMappingJson());
            try {
                restClient.performRequest(createRequest);
                indexEnsured = true;
            } catch (IOException ex) {
                throw new IllegalStateException("create elasticsearch index failed", ex);
            }
        }
    }

    private String indexMappingJson() {
        String analyzer = "ik_max_word";
        String searchAnalyzer = "ik_smart";
        if (!StringUtils.hasText(analyzer) || !StringUtils.hasText(searchAnalyzer)) {
            throw new IllegalStateException("elasticsearch analyzer is missing");
        }
        return """
                {
                  "mappings": {
                    "properties": {
                      "productId": {"type": "long"},
                      "productNo": {"type": "keyword"},
                      "ownerUserId": {"type": "long"},
                      "title": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
                      "description": {"type": "text", "analyzer": "ik_smart", "search_analyzer": "ik_smart"},
                      "coverObjectKey": {"type": "keyword", "index": false},
                      "categoryCode": {"type": "keyword"},
                      "subCategoryCode": {"type": "keyword"},
                      "conditionLevel": {"type": "keyword"},
                      "tradeMode": {"type": "keyword"},
                      "campusCode": {"type": "keyword"},
                      "minPriceCent": {"type": "long"},
                      "maxPriceCent": {"type": "long"},
                      "totalStock": {"type": "integer"},
                      "status": {"type": "integer"},
                      "version": {"type": "long"},
                      "createdAt": {"type": "date"},
                      "occurredAt": {"type": "date"}
                    }
                  }
                }
                """.getBytes(StandardCharsets.UTF_8).length > 0 ? """
                {
                  "mappings": {
                    "properties": {
                      "productId": {"type": "long"},
                      "productNo": {"type": "keyword"},
                      "ownerUserId": {"type": "long"},
                      "title": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
                      "description": {"type": "text", "analyzer": "ik_smart", "search_analyzer": "ik_smart"},
                      "coverObjectKey": {"type": "keyword", "index": false},
                      "categoryCode": {"type": "keyword"},
                      "subCategoryCode": {"type": "keyword"},
                      "conditionLevel": {"type": "keyword"},
                      "tradeMode": {"type": "keyword"},
                      "campusCode": {"type": "keyword"},
                      "minPriceCent": {"type": "long"},
                      "maxPriceCent": {"type": "long"},
                      "totalStock": {"type": "integer"},
                      "status": {"type": "integer"},
                      "version": {"type": "long"},
                      "createdAt": {"type": "date"},
                      "occurredAt": {"type": "date"}
                    }
                  }
                }
                """ : "";
    }
}
