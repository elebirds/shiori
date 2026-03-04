package moe.hhm.shiori.product.service;

import java.util.List;
import moe.hhm.shiori.product.domain.ProductStatus;
import moe.hhm.shiori.product.dto.v2.ProductV2PageResponse;
import moe.hhm.shiori.product.model.ProductV2Record;
import moe.hhm.shiori.product.repository.ProductMapper;
import moe.hhm.shiori.product.storage.OssObjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductV2ServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OssObjectService ossObjectService;

    @Mock
    private ProductService productService;

    @Mock
    private ProductMetrics productMetrics;

    @InjectMocks
    private ProductV2Service productV2Service;

    @Test
    void shouldListOnlyOnSaleProductsByOwner() {
        int onSaleStatus = ProductStatus.ON_SALE.getCode();
        when(productMapper.countProductsByOwnerV2(1001L, "java", onSaleStatus, null, null, null, null))
                .thenReturn(1L);
        when(productMapper.listProductsByOwnerV2(1001L, "java", onSaleStatus, null, null, null, null,
                "CREATED_AT", "DESC", 10, 0))
                .thenReturn(List.of(
                        new ProductV2Record(
                                1L,
                                "P001",
                                1001L,
                                "Java Book",
                                "desc",
                                "product/1001/202603/a.jpg",
                                "TEXTBOOK",
                                "GOOD",
                                "MEETUP",
                                "main_campus",
                                onSaleStatus,
                                0,
                                3900L,
                                3900L,
                                8
                        )
                ));
        when(ossObjectService.presignGetUrl("product/1001/202603/a.jpg")).thenReturn("http://cdn/a.jpg");

        ProductV2PageResponse response = productV2Service.listOnSaleProductsByOwner(
                1001L, "java", null, null, null, null, null, null, 1, 10
        );

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().status()).isEqualTo(ProductStatus.ON_SALE.name());
        verify(productMapper).countProductsByOwnerV2(1001L, "java", onSaleStatus, null, null, null, null);
        verify(productMapper).listProductsByOwnerV2(1001L, "java", onSaleStatus, null, null, null, null,
                "CREATED_AT", "DESC", 10, 0);
        verify(productMetrics).incQuery("keyword");
    }
}
