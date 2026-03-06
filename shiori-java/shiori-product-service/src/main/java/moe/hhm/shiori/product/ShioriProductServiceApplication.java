package moe.hhm.shiori.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ShioriProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShioriProductServiceApplication.class, args);
    }

}
