package moe.hhm.shiori.product;

import moe.hhm.shiori.product.repository.ProductMapper;
import moe.hhm.shiori.product.repository.ProductMetaMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "security.gateway-sign.internal-secret=test-gateway-sign-secret-32-bytes-0001"
})
class ShioriProductServiceApplicationTests {

    @MockitoBean
    private ProductMapper productMapper;

    @MockitoBean
    private ProductMetaMapper productMetaMapper;

    @Test
    void contextLoads() {
    }

}
