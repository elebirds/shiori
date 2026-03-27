package moe.hhm.shiori.search.config;

import moe.hhm.shiori.search.mq.ElasticProductSearchIndexRepository;
import moe.hhm.shiori.search.mq.ProductSearchIndexRepository;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(ElasticSearchProperties.class)
public class ElasticSearchConfig {

    @Bean(destroyMethod = "close")
    RestClient restClient(ElasticSearchProperties properties) {
        URI uri = URI.create(properties.getEndpoint());
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        return RestClient.builder(new HttpHost(uri.getHost(), port, uri.getScheme())).build();
    }

    @Bean
    ProductSearchIndexRepository productSearchIndexRepository(RestClient restClient,
                                                              ElasticSearchProperties properties,
                                                              ObjectMapper objectMapper) {
        return new ElasticProductSearchIndexRepository(restClient, properties.getIndexName(), objectMapper);
    }
}
