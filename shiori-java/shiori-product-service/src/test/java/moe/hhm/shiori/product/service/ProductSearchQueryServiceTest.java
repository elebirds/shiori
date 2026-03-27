package moe.hhm.shiori.product.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import moe.hhm.shiori.common.storage.OssObjectService;
import moe.hhm.shiori.product.config.ProductSearchProperties;
import org.apache.http.HttpEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchQueryServiceTest {

    @Mock
    private RestClient restClient;
    @Mock
    private OssObjectService ossObjectService;
    @Mock
    private Response response;
    @Mock
    private HttpEntity httpEntity;

    private ProductSearchProperties properties;
    private ProductSearchQueryService queryService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new ProductSearchProperties();
        properties.setEnabled(true);
        properties.setIndexName("product-search");
        properties.setMysqlFallbackEnabled(true);
        objectMapper = new ObjectMapper();
        queryService = new ProductSearchQueryService(restClient, properties, ossObjectService, objectMapper);
    }

    @Test
    void shouldBuildDefaultScoreSortAndMapResponse() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream("""
                {
                  "hits": {
                    "total": {"value": 1},
                    "hits": [
                      {
                        "_source": {
                          "productId": 1001,
                          "productNo": "P001",
                          "ownerUserId": 2002,
                          "title": "Java Book",
                          "description": "desc",
                          "coverObjectKey": "product/2002/202603/a.jpg",
                          "categoryCode": "TEXTBOOK",
                          "subCategoryCode": "TEXTBOOK_UNSPEC",
                          "conditionLevel": "GOOD",
                          "tradeMode": "MEETUP",
                          "campusCode": "main",
                          "minPriceCent": 3900,
                          "maxPriceCent": 4900,
                          "totalStock": 8,
                          "status": 2
                        }
                      }
                    ]
                  }
                }
                """.getBytes(StandardCharsets.UTF_8)));
        when(ossObjectService.presignGetUrl("product/2002/202603/a.jpg")).thenReturn("http://cdn/a.jpg");

        var responseBody = queryService.searchOnSaleProducts("Java",
                new ProductSearchQueryService.SearchRequest(null, null, null, null, null,
                        "CREATED_AT", "DESC", 1, 10, false));

        assertThat(responseBody.total()).isEqualTo(1L);
        assertThat(responseBody.items()).hasSize(1);
        assertThat(responseBody.items().getFirst().coverImageUrl()).isEqualTo("http://cdn/a.jpg");

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(requestCaptor.capture());
        JsonNode root = objectMapper.readTree(requestCaptor.getValue().getEntity().getContent());
        assertThat(root.path("query").path("bool").path("must")).hasSize(1);
        assertThat(root.path("query").path("bool").path("should")).hasSize(1);
        assertThat(root.path("sort").get(0).path("_score").path("order").asText()).isEqualTo("desc");
        assertThat(root.path("sort").get(1).path("createdAt").path("order").asText()).isEqualTo("desc");
        assertThat(root.path("sort").get(2).path("productId").path("order").asText()).isEqualTo("desc");
    }

    @Test
    void shouldPreferExplicitBusinessSortBeforeScore() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream("""
                {"hits":{"total":{"value":0},"hits":[]}}
                """.getBytes(StandardCharsets.UTF_8)));

        queryService.searchOnSaleProducts("Java",
                new ProductSearchQueryService.SearchRequest("TEXTBOOK", null, null, null, "main",
                        "MIN_PRICE", "ASC", 2, 20, true));

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(requestCaptor.capture());
        JsonNode root = objectMapper.readTree(requestCaptor.getValue().getEntity().getContent());
        assertThat(root.path("query").path("bool").path("filter")).hasSize(3);
        assertThat(root.path("sort").get(0).path("minPriceCent").path("order").asText()).isEqualTo("asc");
        assertThat(root.path("sort").get(1).path("_score").path("order").asText()).isEqualTo("desc");
        assertThat(root.path("from").asInt()).isEqualTo(20);
        assertThat(root.path("size").asInt()).isEqualTo(20);
    }
}
