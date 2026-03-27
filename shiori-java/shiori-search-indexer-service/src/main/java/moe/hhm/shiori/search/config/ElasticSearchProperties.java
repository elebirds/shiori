package moe.hhm.shiori.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search.elasticsearch")
public class ElasticSearchProperties {

    private String endpoint = "http://127.0.0.1:9200";
    private String indexName = "shiori_product_search";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }
}
