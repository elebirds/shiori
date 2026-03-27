package moe.hhm.shiori.product.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.storage.OssObjectService;
import moe.hhm.shiori.product.config.ProductSearchProperties;
import moe.hhm.shiori.product.domain.ProductStatus;
import moe.hhm.shiori.product.dto.v2.ProductV2PageResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2SummaryResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProductSearchQueryService {

    private static final int ON_SALE_STATUS = ProductStatus.ON_SALE.getCode();

    private final RestClient restClient;
    private final ProductSearchProperties properties;
    private final OssObjectService ossObjectService;
    private final ObjectMapper objectMapper;

    public ProductSearchQueryService(@Nullable RestClient restClient,
                                     ProductSearchProperties properties,
                                     OssObjectService ossObjectService,
                                     ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.ossObjectService = ossObjectService;
        this.objectMapper = objectMapper;
    }

    public boolean isSearchEnabled() {
        return properties.isEnabled() && restClient != null;
    }

    public boolean isMysqlFallbackEnabled() {
        return properties.isMysqlFallbackEnabled();
    }

    public ProductV2PageResponse searchOnSaleProducts(String keyword, SearchRequest request) {
        if (!isSearchEnabled()) {
            throw new IllegalStateException("product search is disabled");
        }
        if (!StringUtils.hasText(keyword)) {
            return new ProductV2PageResponse(0, request.page(), request.size(), List.of());
        }

        Request searchRequest = new Request("POST", "/" + properties.getIndexName() + "/_search");
        try {
            searchRequest.setJsonEntity(objectMapper.writeValueAsString(buildSearchBody(keyword.trim(), request)));
            Response response = restClient.performRequest(searchRequest);
            return parseResponse(response, request.page(), request.size());
        } catch (IOException ex) {
            throw new IllegalStateException("query product search index failed", ex);
        }
    }

    private Map<String, Object> buildSearchBody(String keyword, SearchRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("track_total_hits", true);
        body.put("from", (request.page() - 1) * request.size());
        body.put("size", request.size());
        body.put("_source", List.of(
                "productId",
                "productNo",
                "ownerUserId",
                "title",
                "description",
                "coverObjectKey",
                "categoryCode",
                "subCategoryCode",
                "conditionLevel",
                "tradeMode",
                "campusCode",
                "minPriceCent",
                "maxPriceCent",
                "totalStock",
                "status"
        ));
        body.put("query", Map.of("bool", buildBoolQuery(keyword, request)));
        body.put("sort", buildSort(request));
        return body;
    }

    private Map<String, Object> buildBoolQuery(String keyword, SearchRequest request) {
        Map<String, Object> multiMatch = new LinkedHashMap<>();
        multiMatch.put("query", keyword);
        multiMatch.put("fields", List.of("title^5", "productNo^6", "description^2"));
        multiMatch.put("type", "best_fields");

        Map<String, Object> titlePhrase = new LinkedHashMap<>();
        titlePhrase.put("query", keyword);
        titlePhrase.put("boost", 8);

        List<Map<String, Object>> filter = new ArrayList<>();
        filter.add(termFilter("status", ON_SALE_STATUS));
        appendFilter(filter, "categoryCode", request.categoryCode());
        appendFilter(filter, "subCategoryCode", request.subCategoryCode());
        appendFilter(filter, "conditionLevel", request.conditionLevel());
        appendFilter(filter, "tradeMode", request.tradeMode());
        appendFilter(filter, "campusCode", request.campusCode());

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", List.of(Map.of("multi_match", multiMatch)));
        bool.put("should", List.of(Map.of("match_phrase", Map.of("title", titlePhrase))));
        bool.put("filter", filter);
        return bool;
    }

    private List<Map<String, Object>> buildSort(SearchRequest request) {
        List<Map<String, Object>> sort = new ArrayList<>();
        if (request.explicitSort()) {
            sort.add(sortField(sortFieldName(request.sortBy()), request.sortDir()));
            sort.add(sortField("_score", "DESC"));
            if (!"CREATED_AT".equals(request.sortBy())) {
                sort.add(sortField("createdAt", "DESC"));
            }
            sort.add(sortField("productId", "DESC"));
            return sort;
        }

        sort.add(sortField("_score", "DESC"));
        sort.add(sortField("createdAt", request.sortDir()));
        sort.add(sortField("productId", "DESC"));
        return sort;
    }

    private ProductV2PageResponse parseResponse(Response response, int page, int size) throws IOException {
        JsonNode root = objectMapper.readTree(response.getEntity().getContent());
        JsonNode hitsNode = root.path("hits");
        long total = parseTotal(hitsNode.path("total"));
        List<ProductV2SummaryResponse> items = new ArrayList<>();
        for (JsonNode hit : hitsNode.path("hits")) {
            JsonNode source = hit.path("_source");
            items.add(new ProductV2SummaryResponse(
                    longValue(source, "productId"),
                    textValue(source, "productNo"),
                    longValue(source, "ownerUserId"),
                    textValue(source, "title"),
                    textValue(source, "description"),
                    textValue(source, "coverObjectKey"),
                    resolveCoverImageUrl(textValue(source, "coverObjectKey")),
                    ProductStatus.fromCode(intValue(source, "status", ON_SALE_STATUS)).name(),
                    textValue(source, "categoryCode"),
                    textValue(source, "subCategoryCode"),
                    textValue(source, "conditionLevel"),
                    textValue(source, "tradeMode"),
                    textValue(source, "campusCode"),
                    longValue(source, "minPriceCent"),
                    longValue(source, "maxPriceCent"),
                    intObjectValue(source, "totalStock")
            ));
        }
        return new ProductV2PageResponse(total, page, size, items);
    }

    private void appendFilter(List<Map<String, Object>> filters, String field, @Nullable String value) {
        if (StringUtils.hasText(value)) {
            filters.add(termFilter(field, value));
        }
    }

    private Map<String, Object> termFilter(String field, Object value) {
        return Map.of("term", Map.of(field, value));
    }

    private Map<String, Object> sortField(String field, String direction) {
        return Map.of(field, Map.of("order", direction.toLowerCase()));
    }

    private String sortFieldName(String sortBy) {
        return switch (sortBy) {
            case "MIN_PRICE" -> "minPriceCent";
            case "MAX_PRICE" -> "maxPriceCent";
            case "CREATED_AT" -> "createdAt";
            default -> "createdAt";
        };
    }

    private long parseTotal(JsonNode totalNode) {
        if (totalNode.isObject()) {
            return totalNode.path("value").asLong();
        }
        return totalNode.asLong(0L);
    }

    private String resolveCoverImageUrl(String coverObjectKey) {
        if (!StringUtils.hasText(coverObjectKey)) {
            return null;
        }
        try {
            return ossObjectService.presignGetUrl(coverObjectKey);
        } catch (BizException ignored) {
            return null;
        }
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private Integer intObjectValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }

    public record SearchRequest(
            String categoryCode,
            String subCategoryCode,
            String conditionLevel,
            String tradeMode,
            String campusCode,
            String sortBy,
            String sortDir,
            int page,
            int size,
            boolean explicitSort
    ) {
    }
}
