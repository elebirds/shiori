package moe.hhm.shiori.product.service;

import java.util.List;
import moe.hhm.shiori.product.domain.ProductStatus;
import moe.hhm.shiori.product.dto.SkuInput;
import moe.hhm.shiori.product.dto.v2.CreateProductV2Request;
import moe.hhm.shiori.product.model.ProductEntity;
import moe.hhm.shiori.product.model.ProductV2Record;
import moe.hhm.shiori.product.repository.ProductMapper;
import moe.hhm.shiori.product.storage.OssObjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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

    private ProductV2Service productV2Service;

    @BeforeEach
    void setUp() {
        productV2Service = new ProductV2Service(productMapper, ossObjectService, productService, productMetrics);
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
                <p class="rt-fs-lg evil-class">hello<script>alert('x')</script></p>
                <img src="http://evil.local/a.jpg" onerror="alert('x')" />
                <img src="blob:http://local/123" data-object-key="product/1001/202603/ok.jpg" />
                """,
                "product/1001/202603/cover.jpg",
                "TEXTBOOK",
                "GOOD",
                "MEETUP",
                "main_campus",
                List.of(new SkuInput(null, "标准版", "{}", 3900L, 8))
        );

        productV2Service.createProduct(1001L, request);

        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productMapper).insertProduct(productCaptor.capture());
        String sanitized = productCaptor.getValue().getDetailHtml();

        assertThat(sanitized).contains("class=\"rt-fs-lg\"");
        assertThat(sanitized).doesNotContain("evil-class");
        assertThat(sanitized).doesNotContain("script");
        assertThat(sanitized).doesNotContain("http://evil.local/a.jpg");
        assertThat(sanitized).contains("src=\"product/1001/202603/ok.jpg\"");
        assertThat(sanitized).contains("data-object-key=\"product/1001/202603/ok.jpg\"");
        verify(productMapper).insertSku(any());
    }

    @Test
    void shouldReturnSignedImageUrlInDetailHtml() {
        when(productMapper.findOnSaleProductV2ById(1L)).thenReturn(new ProductV2Record(
                1L,
                "P001",
                1001L,
                "Java Book",
                "summary",
                "<p><img src=\"product/1001/202603/ok.jpg\" data-object-key=\"product/1001/202603/ok.jpg\" /></p>",
                "product/1001/202603/cover.jpg",
                "TEXTBOOK",
                "GOOD",
                "MEETUP",
                "main_campus",
                ProductStatus.ON_SALE.getCode(),
                0,
                3900L,
                3900L,
                8
        ));
        when(productMapper.listActiveSkusByProductId(1L)).thenReturn(List.of());
        when(ossObjectService.presignGetUrl(eq("product/1001/202603/ok.jpg")))
                .thenReturn("http://cdn.local/signed-ok.jpg");
        when(ossObjectService.presignGetUrl(eq("product/1001/202603/cover.jpg")))
                .thenReturn("http://cdn.local/signed-cover.jpg");

        var detail = productV2Service.getOnSaleProductDetail(1L);

        assertThat(detail.detailHtml()).contains("http://cdn.local/signed-ok.jpg");
        assertThat(detail.detailHtml()).contains("data-object-key=\"product/1001/202603/ok.jpg\"");
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
                "GOOD",
                "MEETUP",
                "main_campus",
                List.of(new SkuInput(null, "标准版", "{}", 3900L, 8))
        );

        productV2Service.createProduct(1001L, request);

        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productMapper).insertProduct(productCaptor.capture());
        assertThat(productCaptor.getValue().getDetailHtml()).isNull();
    }
}
