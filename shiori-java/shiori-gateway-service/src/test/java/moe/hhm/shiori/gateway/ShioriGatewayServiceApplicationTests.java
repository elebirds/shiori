package moe.hhm.shiori.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "security.jwt.hmac-secret=test-secret-test-secret-test-secret-1234",
        "security.jwt.issuer=shiori",
        "security.gateway-sign.internal-secret=test-gateway-sign-secret-32-bytes-0001"
})
class ShioriGatewayServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
