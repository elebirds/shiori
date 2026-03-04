package moe.hhm.shiori.product.service;

import java.util.List;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.domain.ProductStatus;
import moe.hhm.shiori.product.dto.CreateProductRequest;
import moe.hhm.shiori.product.dto.ProductPageResponse;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.SkuInput;
import moe.hhm.shiori.product.dto.UpdateProductRequest;
import moe.hhm.shiori.product.model.ProductEntity;
import moe.hhm.shiori.product.model.ProductRecord;
import moe.hhm.shiori.product.model.SkuRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import moe.hhm.shiori.product.storage.OssObjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OssObjectService ossObjectService;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldCreateProductWithMultipleSkus() {
        CreateProductRequest request = new CreateProductRequest(
                "Java Book",
                "good condition",
                "product/1001/202603/cover.jpg",
                List.of(
                        new SkuInput(null, "标准版", "{\"binding\":\"paper\"}", 3900L, 12),
                        new SkuInput(null, "含习题", "{\"binding\":\"paper\"}", 4500L, 5)
                )
        );
        doAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(productMapper).insertProduct(any(ProductEntity.class));

        ProductWriteResponse response = productService.createProduct(1001L, request);

        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(ProductStatus.DRAFT.name());
        verify(productMapper).insertProduct(any(ProductEntity.class));
        verify(productMapper, times(2)).insertSku(any());
    }

    @Test
    void shouldRejectUpdateWhenNotOwnerAndNotAdmin() {
        when(productMapper.findProductById(1L)).thenReturn(new ProductRecord(
                1L, "P001", 2002L, "Java Book", "desc", "product/2002/202603/a.jpg", 1, 0
        ));

        UpdateProductRequest request = new UpdateProductRequest(
                "New title",
                "new desc",
                "product/2002/202603/a.jpg",
                List.of(new SkuInput(10L, "标准版", "{}", 3900L, 10))
        );

        assertThatThrownBy(() -> productService.updateProduct(1L, 1001L, false, request))
                .isInstanceOf(BizException.class);

        verify(productMapper, never()).updateProductBase(anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldPublishProductWhenHasSku() {
        when(productMapper.findProductById(1L)).thenReturn(new ProductRecord(
                1L, "P001", 1001L, "Java Book", "desc", null, 1, 0
        ));
        when(productMapper.listActiveSkusByProductId(1L)).thenReturn(List.of(
                new SkuRecord(10L, 1L, "S001", "标准版", "{}", 3900L, 12, 0)
        ));

        ProductWriteResponse response = productService.publishProduct(1L, 1001L, false);

        assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE.name());
        verify(productMapper).updateProductStatusById(1L, ProductStatus.ON_SALE.getCode());
    }

    @Test
    void shouldRejectOffShelfWhenNotOnSale() {
        when(productMapper.findProductById(1L)).thenReturn(new ProductRecord(
                1L, "P001", 1001L, "Java Book", "desc", null, 1, 0
        ));

        assertThatThrownBy(() -> productService.offShelfProduct(1L, 1001L, false))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldUpdateSkuAndDeleteRemovedSkus() {
        when(productMapper.findProductById(1L))
                .thenReturn(new ProductRecord(1L, "P001", 1001L, "Old", "Old", null, 1, 0))
                .thenReturn(new ProductRecord(1L, "P001", 1001L, "New", "New", null, 1, 0));
        when(productMapper.listActiveSkusByProductId(1L)).thenReturn(List.of(
                new SkuRecord(10L, 1L, "S001", "旧SKU", "{}", 3900L, 12, 0),
                new SkuRecord(11L, 1L, "S002", "待删除SKU", "{}", 4500L, 5, 0)
        ));

        UpdateProductRequest request = new UpdateProductRequest(
                "New",
                "New",
                null,
                List.of(new SkuInput(10L, "新SKU", "{\"k\":\"v\"}", 5000L, 3))
        );

        ProductWriteResponse response = productService.updateProduct(1L, 1001L, false, request);

        assertThat(response.productId()).isEqualTo(1L);
        ArgumentCaptor<Long> removedSkuCaptor = ArgumentCaptor.forClass(Long.class);
        verify(productMapper).softDeleteSkuById(removedSkuCaptor.capture(), eq(1L));
        assertThat(removedSkuCaptor.getValue()).isEqualTo(11L);
    }

    @Test
    void shouldListMyProductsWithStatusFilter() {
        when(productMapper.countProductsByOwner(1001L, "java", ProductStatus.ON_SALE.getCode())).thenReturn(1L);
        when(productMapper.listProductsByOwner(1001L, "java", ProductStatus.ON_SALE.getCode(), 10, 0)).thenReturn(List.of(
                new ProductRecord(1L, "P001", 1001L, "Java Book", "desc", null, ProductStatus.ON_SALE.getCode(), 0)
        ));

        ProductPageResponse response = productService.listMyProducts(1001L, "java", "ON_SALE", 1, 10);

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().status()).isEqualTo(ProductStatus.ON_SALE.name());
    }

    @Test
    void shouldRejectUnknownStatusWhenListMyProducts() {
        assertThatThrownBy(() -> productService.listMyProducts(1001L, null, "UNKNOWN", 1, 10))
                .isInstanceOf(BizException.class);
    }
}
