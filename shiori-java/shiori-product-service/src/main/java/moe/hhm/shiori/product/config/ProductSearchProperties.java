package moe.hhm.shiori.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product.search")
public class ProductSearchProperties {

    private boolean enabled;
    private boolean mysqlFallbackEnabled = true;
    private String endpoint = "http://127.0.0.1:9200";
    private String indexName = "product-search";
    private int connectTimeout = 1000;
    private int readTimeout = 1500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMysqlFallbackEnabled() {
        return mysqlFallbackEnabled;
    }

    public void setMysqlFallbackEnabled(boolean mysqlFallbackEnabled) {
        this.mysqlFallbackEnabled = mysqlFallbackEnabled;
    }

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

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
}
