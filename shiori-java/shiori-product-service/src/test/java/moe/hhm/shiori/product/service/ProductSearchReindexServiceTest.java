package moe.hhm.shiori.product.service;

import java.util.List;
import moe.hhm.shiori.product.dto.ProductSearchReindexResponse;
import moe.hhm.shiori.product.repository.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchReindexServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductService productService;

    @Test
    void shouldReindexOnlyOnSaleProductsInBatches() {
        when(productMapper.listOnSaleProductIdsAfterId(null, 2)).thenReturn(List.of(1L, 2L));
        when(productMapper.listOnSaleProductIdsAfterId(2L, 2)).thenReturn(List.of(3L));

        ProductSearchReindexService reindexService = new ProductSearchReindexService(productMapper, productService);

        ProductSearchReindexResponse response = reindexService.reindexAllOnSaleProducts(2);

        assertThat(response.reindexedCount()).isEqualTo(3);
        assertThat(response.batchCount()).isEqualTo(2);
        assertThat(response.lastProductId()).isEqualTo(3L);
        verify(productMapper, times(2)).listOnSaleProductIdsAfterId(org.mockito.ArgumentMatchers.any(), eq(2));
        verify(productService).appendProductSearchUpsertOutbox(1L);
        verify(productService).appendProductSearchUpsertOutbox(2L);
        verify(productService).appendProductSearchUpsertOutbox(3L);
    }
}
