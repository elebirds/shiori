package moe.hhm.shiori.product.config;

import java.net.URI;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProductSearchProperties.class)
public class ProductSearchEsConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "product.search", name = "enabled", havingValue = "true")
    public RestClient productSearchRestClient(ProductSearchProperties properties) {
        URI uri = URI.create(properties.getEndpoint());
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        return RestClient.builder(new HttpHost(uri.getHost(), port, uri.getScheme()))
                .setRequestConfigCallback(builder -> builder
                        .setConnectTimeout(properties.getConnectTimeout())
                        .setSocketTimeout(properties.getReadTimeout()))
                .build();
    }
}
