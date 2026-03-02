package moe.hhm.shiori.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ShioriOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShioriOrderServiceApplication.class, args);
    }
}
