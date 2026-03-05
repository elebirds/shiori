package moe.hhm.shiori.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ShioriPaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShioriPaymentServiceApplication.class, args);
    }
}
