package moe.hhm.shiori.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShioriUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShioriUserServiceApplication.class, args);
    }

}
