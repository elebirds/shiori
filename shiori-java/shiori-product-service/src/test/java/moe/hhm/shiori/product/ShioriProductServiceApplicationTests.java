package moe.hhm.shiori.product;

import moe.hhm.shiori.product.repository.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ShioriProductServiceApplicationTests {

    @MockitoBean
    private ProductMapper productMapper;

    @Test
    void contextLoads() {
    }

}
