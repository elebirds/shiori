package moe.hhm.shiori.product.admin.service;

import java.util.List;
import java.util.Map;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.domain.ProductStatus;
import moe.hhm.shiori.product.dto.ProductDetailResponse;
import moe.hhm.shiori.product.dto.ProductPageResponse;
import moe.hhm.shiori.product.dto.ProductSummaryResponse;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.model.ProductRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import moe.hhm.shiori.product.service.ProductService;
import moe.hhm.shiori.product.storage.OssObjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminProductService {

    private final ProductMapper productMapper;
    private final ProductService productService;
    private final OssObjectService ossObjectService;
    private final ObjectMapper objectMapper;

    public AdminProductService(ProductMapper productMapper,
                               ProductService productService,
                               OssObjectService ossObjectService,
                               ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.productService = productService;
        this.ossObjectService = ossObjectService;
        this.objectMapper = objectMapper;
    }

    public ProductPageResponse listProducts(String keyword, String status, Long ownerUserId, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        Integer statusCode = parseStatusCode(status);
        int offset = (normalizedPage - 1) * normalizedSize;

        long total = productMapper.countProductsForAdmin(keyword, statusCode, ownerUserId);
        List<ProductSummaryResponse> items = productMapper.listProductsForAdmin(
                        keyword,
                        statusCode,
                        ownerUserId,
                        normalizedSize,
                        offset
                ).stream()
                .map(this::toSummary)
                .toList();
        return new ProductPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public ProductDetailResponse getProductDetail(Long productId, Long operatorUserId) {
        return productService.getMyProductDetail(productId, operatorUserId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductWriteResponse forceOffShelf(Long productId, Long operatorUserId, String reason) {
        ProductRecord before = requireProduct(productId);
        ProductStatus beforeStatus = ProductStatus.fromCode(before.status());

        ProductWriteResponse response;
        if (beforeStatus == ProductStatus.OFF_SHELF) {
            response = new ProductWriteResponse(before.id(), before.productNo(), ProductStatus.OFF_SHELF.name());
        } else if (beforeStatus == ProductStatus.ON_SALE) {
            response = productService.offShelfProduct(productId, operatorUserId, true);
        } else {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_STATUS, HttpStatus.BAD_REQUEST);
        }

        ProductRecord after = requireProduct(productId);
        productMapper.insertAdminAuditLog(
                operatorUserId,
                productId,
                "PRODUCT_FORCE_OFF_SHELF",
                snapshot(before),
                snapshot(after),
                StringUtils.hasText(reason) ? reason.trim() : null
        );
        return response;
    }

    private ProductSummaryResponse toSummary(ProductRecord record) {
        return new ProductSummaryResponse(
                record.id(),
                record.productNo(),
                record.title(),
                record.description(),
                record.coverObjectKey(),
                resolveCoverImageUrl(record.coverObjectKey()),
                ProductStatus.fromCode(record.status()).name()
        );
    }

    private ProductRecord requireProduct(Long productId) {
        ProductRecord record = productMapper.findProductById(productId);
        if (record == null || (record.isDeleted() != null && record.isDeleted() == 1)) {
            throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return record;
    }

    private String resolveCoverImageUrl(String coverObjectKey) {
        if (!StringUtils.hasText(coverObjectKey)) {
            return null;
        }
        try {
            return ossObjectService.presignGetUrl(coverObjectKey);
        } catch (BizException ignored) {
            return null;
        }
    }

    private Integer parseStatusCode(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return ProductStatus.valueOf(status.trim().toUpperCase()).getCode();
        } catch (IllegalArgumentException ex) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
    }

    private String snapshot(ProductRecord record) {
        Map<String, Object> state = Map.of(
                "status", ProductStatus.fromCode(record.status()).name(),
                "ownerUserId", record.ownerUserId(),
                "title", record.title(),
                "coverObjectKey", StringUtils.hasText(record.coverObjectKey()) ? record.coverObjectKey() : ""
        );
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JacksonException e) {
            return "{}";
        }
    }
}
