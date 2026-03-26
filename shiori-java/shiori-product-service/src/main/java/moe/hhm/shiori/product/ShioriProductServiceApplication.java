package moe.hhm.shiori.product;

import moe.hhm.shiori.product.config.ProductDetailCacheProperties;
import moe.hhm.shiori.product.config.ProductMediaUrlCacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({ProductDetailCacheProperties.class, ProductMediaUrlCacheProperties.class})
@SpringBootApplication
public class ShioriProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShioriProductServiceApplication.class, args);
    }

}
