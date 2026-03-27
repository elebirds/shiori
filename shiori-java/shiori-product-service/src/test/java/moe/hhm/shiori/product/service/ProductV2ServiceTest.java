package moe.hhm.shiori.product.service;

import java.util.List;
import moe.hhm.shiori.product.domain.ProductStatus;
import moe.hhm.shiori.product.dto.SpecItemInput;
import moe.hhm.shiori.product.dto.SkuInput;
import moe.hhm.shiori.product.dto.v2.CreateProductV2Request;
import moe.hhm.shiori.product.dto.v2.ProductV2PageResponse;
import moe.hhm.shiori.product.model.ProductEntity;
import moe.hhm.shiori.product.model.ProductV2Record;
import moe.hhm.shiori.product.repository.ProductMapper;
import moe.hhm.shiori.common.storage.OssObjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
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

    @Mock
    private ProductMetaService productMetaService;

    @Mock
    private ProductDetailCacheService productDetailCacheService;
    @Mock
    private ProductSearchQueryService productSearchQueryService;

    private ProductV2Service productV2Service;

    @BeforeEach
    void setUp() {
        productV2Service = new ProductV2Service(
                productMapper,
                ossObjectService,
                productService,
                productMetrics,
                new SkuSpecCodec(new ObjectMapper()),
                productMetaService,
                productDetailCacheService,
                productSearchQueryService
        );
    }

    @Test
    void shouldUseEsWhenKeywordPresent() {
        when(productSearchQueryService.isSearchEnabled()).thenReturn(true);
        when(productSearchQueryService.searchOnSaleProducts(eq("Java"),
                any(ProductSearchQueryService.SearchRequest.class)))
                .thenReturn(new ProductV2PageResponse(
                        1L,
                        1,
                        10,
                        List.of(new moe.hhm.shiori.product.dto.v2.ProductV2SummaryResponse(
                                1L,
                                "P001",
                                1001L,
                                "Java Book",
                                "desc",
                                "product/1001/202603/a.jpg",
                                "http://cdn/a.jpg",
                                "ON_SALE",
                                "TEXTBOOK",
                                "TEXTBOOK_UNSPEC",
                                "GOOD",
                                "MEETUP",
                                "main_campus",
                                3900L,
                                3900L,
                                8
                        ))
                ));

        ProductV2PageResponse response = productV2Service.listOnSaleProducts(
                "Java", null, null, null, null, null, null, null, 1, 10
        );

        assertThat(response.total()).isEqualTo(1L);
        verify(productSearchQueryService).searchOnSaleProducts(eq("Java"),
                any(ProductSearchQueryService.SearchRequest.class));
        verify(productMapper, never()).listOnSaleProductsV2(any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt());
        verify(productMetrics).incQuery("keyword");
    }

    @Test
    void shouldFallbackToMysqlWhenEsQueryThrows() {
        when(productSearchQueryService.isSearchEnabled()).thenReturn(true);
        when(productSearchQueryService.isMysqlFallbackEnabled()).thenReturn(true);
        when(productSearchQueryService.searchOnSaleProducts(eq("Java"),
                any(ProductSearchQueryService.SearchRequest.class)))
                .thenThrow(new RuntimeException("es down"));
        when(productMapper.countOnSaleProductsV2("Java", null, null, null, null, null)).thenReturn(1L);
        when(productMapper.listOnSaleProductsV2("Java", null, null, null, null, null,
                "CREATED_AT", "DESC", 10, 0))
                .thenReturn(List.of(new ProductV2Record(
                        1L,
                        "P001",
                        1001L,
                        "Java Book",
                        "desc",
                        null,
                        "product/1001/202603/a.jpg",
                        "TEXTBOOK",
                        "TEXTBOOK_UNSPEC",
                        "GOOD",
                        "MEETUP",
                        "main_campus",
                        ProductStatus.ON_SALE.getCode(),
                        0,
                        3900L,
                        3900L,
                        8
                )));
        when(ossObjectService.presignGetUrl("product/1001/202603/a.jpg")).thenReturn("http://cdn/a.jpg");

        ProductV2PageResponse response = productV2Service.listOnSaleProducts(
                "Java", null, null, null, null, null, null, null, 1, 10
        );

        assertThat(response.total()).isEqualTo(1L);
        verify(productMapper).listOnSaleProductsV2(eq("Java"), any(), any(), any(), any(), any(),
                any(), any(), eq(10), eq(0));
    }

    @Test
    void shouldListOnlyOnSaleProductsByOwner() {
        int onSaleStatus = ProductStatus.ON_SALE.getCode();
        when(productMapper.countProductsByOwnerV2(1001L, "java", onSaleStatus, null, null, null, null, null))
                .thenReturn(1L);
        when(productMapper.listProductsByOwnerV2(1001L, "java", onSaleStatus, null, null, null, null, null,
                "CREATED_AT", "DESC", 10, 0))
                .thenReturn(List.of(
                        new ProductV2Record(
                                1L,
                                "P001",
                                1001L,
                                "Java Book",
                                "desc",
                                null,
                                "product/1001/202603/a.jpg",
                                "TEXTBOOK",
                                "TEXTBOOK_UNSPEC",
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
                1001L, "java", null, null, null, null, null, null, null, 1, 10
        );

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().status()).isEqualTo(ProductStatus.ON_SALE.name());
        verify(productMapper).countProductsByOwnerV2(1001L, "java", onSaleStatus, null, null, null, null, null);
        verify(productMapper).listProductsByOwnerV2(1001L, "java", onSaleStatus, null, null, null, null, null,
                "CREATED_AT", "DESC", 10, 0);
        verify(productMetrics).incQuery("keyword");
    }

    @Test
    void shouldSanitizeDetailHtmlAndNormalizeImageObjectKeyWhenCreate() {
        doAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(productMapper).insertProduct(any(ProductEntity.class));

        CreateProductV2Request request = new CreateProductV2Request(
                "Java Book",
                "summary",
                """
                <p style="font-size:18px;text-align:center;color:red">hello<script>alert('x')</script></p>
                <img src="http://evil.local/a.jpg" onerror="alert('x')" />
                <img src="blob:http://local/123" data-object-key="product/1001/202603/ok.jpg" style="width:30%;height:auto;border:1px solid red" />
                """,
                "product/1001/202603/cover.jpg",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main_campus",
                List.of(skuInput(null, "版本", "标准版", 3900L, 8))
        );

        productV2Service.createProduct(1001L, request);

        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productMapper).insertProduct(productCaptor.capture());
        String sanitized = productCaptor.getValue().getDetailHtml();

        assertThat(sanitized).contains("style=\"font-size:18px;text-align:center\"");
        assertThat(sanitized).doesNotContain("color:red");
        assertThat(sanitized).doesNotContain("script");
        assertThat(sanitized).doesNotContain("http://evil.local/a.jpg");
        assertThat(sanitized).contains("src=\"product/1001/202603/ok.jpg\"");
        assertThat(sanitized).contains("data-object-key=\"product/1001/202603/ok.jpg\"");
        assertThat(sanitized).contains("style=\"width:30%;height:auto\"");
        verify(productMapper).insertSku(any());
    }

    @Test
    void shouldReturnSignedImageUrlInDetailHtml() {
        when(productDetailCacheService.getOnSaleProductDetail(eq(1L), any())).thenReturn(
                new ProductDetailCacheService.CachedProductDetail(
                        1L,
                        "P001",
                        1001L,
                        "Java Book",
                        "summary",
                        "<p style=\"text-align:center\"><span style=\"font-size:18px\">展示内容</span></p>"
                                + "<p><img src=\"product/1001/202603/ok.jpg\" data-object-key=\"product/1001/202603/ok.jpg\" style=\"width:50%\" /></p>",
                        "product/1001/202603/cover.jpg",
                        "TEXTBOOK",
                        "TEXTBOOK_UNSPEC",
                        "GOOD",
                        "MEETUP",
                        "main_campus",
                        3900L,
                        3900L,
                        8,
                        List.of()
                )
        );
        when(ossObjectService.presignGetUrl(eq("product/1001/202603/ok.jpg")))
                .thenReturn("http://cdn.local/signed-ok.jpg");
        when(ossObjectService.presignGetUrl(eq("product/1001/202603/cover.jpg")))
                .thenReturn("http://cdn.local/signed-cover.jpg");

        var detail = productV2Service.getOnSaleProductDetail(1L);

        assertThat(detail.detailHtml()).contains("font-size:18px");
        assertThat(detail.detailHtml()).contains("text-align:center");
        assertThat(detail.detailHtml()).contains("width:50%");
        assertThat(detail.detailHtml()).contains("http://cdn.local/signed-ok.jpg");
        assertThat(detail.detailHtml()).contains("data-object-key=\"product/1001/202603/ok.jpg\"");
        verify(productMapper, never()).findOnSaleProductV2ById(1L);
        verify(productMapper, never()).listActiveSkusByProductId(1L);
    }

    @Test
    void shouldExtractObjectKeyFromSignedImageUrlForStoreAndRender() {
        doAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(productMapper).insertProduct(any(ProductEntity.class));

        CreateProductV2Request request = new CreateProductV2Request(
                "Java Book",
                "summary",
                """
                <p><img src="http://127.0.0.1:9000/shiori-product/product/1001/202603/legacy.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc" /></p>
                """,
                "product/1001/202603/cover.jpg",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main_campus",
                List.of(skuInput(null, "版本", "标准版", 3900L, 8))
        );

        productV2Service.createProduct(1001L, request);

        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productMapper).insertProduct(productCaptor.capture());
        String sanitized = productCaptor.getValue().getDetailHtml();
        assertThat(sanitized).contains("product/1001/202603/legacy.jpg");
        assertThat(sanitized).contains("data-object-key=\"product/1001/202603/legacy.jpg\"");

        when(productDetailCacheService.getOnSaleProductDetail(eq(1L), any())).thenReturn(
                new ProductDetailCacheService.CachedProductDetail(
                        1L,
                        "P001",
                        1001L,
                        "Java Book",
                        "summary",
                        "<p><img src=\"http://127.0.0.1:9000/shiori-product/product/1001/202603/legacy.jpg?temp=1\" /></p>",
                        "product/1001/202603/cover.jpg",
                        "TEXTBOOK",
                        "TEXTBOOK_UNSPEC",
                        "GOOD",
                        "MEETUP",
                        "main_campus",
                        3900L,
                        3900L,
                        8,
                        List.of()
                )
        );
        when(ossObjectService.presignGetUrl(eq("product/1001/202603/legacy.jpg")))
                .thenReturn("http://cdn.local/signed-legacy.jpg");
        when(ossObjectService.presignGetUrl(eq("product/1001/202603/cover.jpg")))
                .thenReturn("http://cdn.local/signed-cover.jpg");

        var detail = productV2Service.getOnSaleProductDetail(1L);

        assertThat(detail.detailHtml()).contains("http://cdn.local/signed-legacy.jpg");
        assertThat(detail.detailHtml()).contains("data-object-key=\"product/1001/202603/legacy.jpg\"");
    }

    @Test
    void shouldStoreNullDetailHtmlWhenBlank() {
        doAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(productMapper).insertProduct(any(ProductEntity.class));

        CreateProductV2Request request = new CreateProductV2Request(
                "Java Book",
                "summary",
                "   ",
                "product/1001/202603/cover.jpg",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main_campus",
                List.of(skuInput(null, "版本", "标准版", 3900L, 8))
        );

        productV2Service.createProduct(1001L, request);

        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productMapper).insertProduct(productCaptor.capture());
        assertThat(productCaptor.getValue().getDetailHtml()).isNull();
    }

    @Test
    void shouldEvictDetailCacheAfterUpdateProduct() {
        when(productMapper.findProductV2ById(1L)).thenReturn(new ProductV2Record(
                1L,
                "P001",
                1001L,
                "Old",
                "summary",
                null,
                "product/1001/202603/cover.jpg",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main_campus",
                ProductStatus.ON_SALE.getCode(),
                0,
                3900L,
                3900L,
                8
        ));
        when(productMapper.listActiveSkusByProductId(1L)).thenReturn(List.of(
                new moe.hhm.shiori.product.model.SkuRecord(10L, 1L, "SKU-10", "标准版", "[]",
                        "sig-a", "标准版", "{}", 3900L, 8, 0)
        ));
        when(productMapper.findProductById(1L)).thenReturn(new moe.hhm.shiori.product.model.ProductRecord(
                1L, "P001", 1001L, "New", "summary", "product/1001/202603/cover.jpg", ProductStatus.ON_SALE.getCode(), 0
        ));

        productV2Service.updateProduct(1L, 1001L, false, new moe.hhm.shiori.product.dto.v2.UpdateProductV2Request(
                "New",
                "summary",
                null,
                "product/1001/202603/cover.jpg",
                "TEXTBOOK",
                "TEXTBOOK_UNSPEC",
                "GOOD",
                "MEETUP",
                "main_campus",
                List.of(skuInput(10L, "版本", "标准版", 3900L, 8))
        ));

        verify(productDetailCacheService).evictProductDetail(1L);
    }

    private SkuInput skuInput(Long id, String specName, String specValue, long priceCent, int stock) {
        return new SkuInput(id, List.of(new SpecItemInput(specName, specValue)), priceCent, stock);
    }
}
