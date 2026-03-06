package moe.hhm.shiori.product.service;

import java.util.List;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.config.ProductMqProperties;
import moe.hhm.shiori.product.config.ProductOutboxProperties;
import moe.hhm.shiori.product.domain.ProductStatus;
import moe.hhm.shiori.product.dto.CreateProductRequest;
import moe.hhm.shiori.product.dto.ProductPageResponse;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.SpecItemInput;
import moe.hhm.shiori.product.dto.SkuInput;
import moe.hhm.shiori.product.dto.UpdateProductRequest;
import moe.hhm.shiori.product.model.ProductEntity;
import moe.hhm.shiori.product.model.ProductOutboxEventEntity;
import moe.hhm.shiori.product.model.ProductRecord;
import moe.hhm.shiori.product.model.ProductV2Record;
import moe.hhm.shiori.product.model.SkuRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import moe.hhm.shiori.common.storage.OssObjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

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

    private ProductService productService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ProductMqProperties productMqProperties = new ProductMqProperties();
        ProductOutboxProperties productOutboxProperties = new ProductOutboxProperties();
        productService = new ProductService(
                productMapper,
                ossObjectService,
                new SkuSpecCodec(new ObjectMapper()),
                productMqProperties,
                productOutboxProperties,
                new ObjectMapper()
        );
    }

    @Test
    void shouldCreateProductWithMultipleSkus() {
        CreateProductRequest request = new CreateProductRequest(
                "Java Book",
                "good condition",
                "product/1001/202603/cover.jpg",
                List.of(
                        skuInput(null, "版本", "标准版", 3900L, 12),
                        skuInput(null, "版本", "含习题", 4500L, 5)
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
                List.of(skuInput(10L, "版本", "标准版", 3900L, 10))
        );

        assertThatThrownBy(() -> productService.updateProduct(1L, 1001L, false, request))
                .isInstanceOf(BizException.class);

        verify(productMapper, never()).updateProductBase(anyLong(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldPublishProductWhenHasSku() {
        when(productMapper.findProductById(1L)).thenReturn(new ProductRecord(
                1L, "P001", 1001L, "Java Book", "desc", null, 1, 0
        ));
        when(productMapper.listActiveSkusByProductId(1L)).thenReturn(List.of(
                new SkuRecord(10L, 1L, "S001", "版本:标准版", "[{\"name\":\"版本\",\"value\":\"标准版\"}]",
                        "sig-a", "版本:标准版", "{\"版本\":\"标准版\"}", 3900L, 12, 0)
        ));
        when(productMapper.findProductV2ById(1L)).thenReturn(new ProductV2Record(
                1L,
                "P001",
                1001L,
                "Java Book",
                "desc",
                null,
                "product/1001/202603/cover.jpg",
                "TEXTBOOK",
                "GOOD",
                "MEETUP",
                "main_campus",
                ProductStatus.ON_SALE.getCode(),
                0,
                3900L,
                3900L,
                12
        ));

        ProductWriteResponse response = productService.publishProduct(1L, 1001L, false);

        assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE.name());
        verify(productMapper).updateProductStatusById(1L, ProductStatus.ON_SALE.getCode());
        verify(productMapper).insertProductOutboxEvent(any(ProductOutboxEventEntity.class));
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
                new SkuRecord(10L, 1L, "S001", "版本:旧SKU", "[{\"name\":\"版本\",\"value\":\"旧SKU\"}]",
                        "sig-old", "版本:旧SKU", "{\"版本\":\"旧SKU\"}", 3900L, 12, 0),
                new SkuRecord(11L, 1L, "S002", "版本:待删除SKU", "[{\"name\":\"版本\",\"value\":\"待删除SKU\"}]",
                        "sig-del", "版本:待删除SKU", "{\"版本\":\"待删除SKU\"}", 4500L, 5, 0)
        ));

        UpdateProductRequest request = new UpdateProductRequest(
                "New",
                "New",
                null,
                List.of(skuInput(10L, "版本", "新SKU", 5000L, 3))
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

    @Test
    void shouldRejectDuplicateSpecCombinationWhenCreateProduct() {
        CreateProductRequest request = new CreateProductRequest(
                "Java Book",
                "good condition",
                "product/1001/202603/cover.jpg",
                List.of(
                        new SkuInput(null, List.of(
                                new SpecItemInput("版本", "标准版"),
                                new SpecItemInput("品相", "良好")
                        ), 3900L, 12),
                        new SkuInput(null, List.of(
                                new SpecItemInput("品相", "良好"),
                                new SpecItemInput("版本", "标准版")
                        ), 3900L, 8)
                )
        );
        doAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(productMapper).insertProduct(any(ProductEntity.class));

        assertThatThrownBy(() -> productService.createProduct(1001L, request))
                .isInstanceOf(BizException.class);
    }

    @Test
    void shouldRejectPublishWhenAllSkuOutOfStock() {
        when(productMapper.findProductById(1L)).thenReturn(new ProductRecord(
                1L, "P001", 1001L, "Java Book", "desc", null, 1, 0
        ));
        when(productMapper.listActiveSkusByProductId(1L)).thenReturn(List.of(
                new SkuRecord(10L, 1L, "S001", "版本:标准版", "[{\"name\":\"版本\",\"value\":\"标准版\"}]",
                        "sig-a", "版本:标准版", "{\"版本\":\"标准版\"}", 3900L, 0, 0)
        ));

        assertThatThrownBy(() -> productService.publishProduct(1L, 1001L, false))
                .isInstanceOf(BizException.class);
    }

    private SkuInput skuInput(Long id, String specName, String specValue, long priceCent, int stock) {
        return new SkuInput(id, List.of(new SpecItemInput(specName, specValue)), priceCent, stock);
    }
}
