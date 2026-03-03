package moe.hhm.shiori.product.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.domain.ProductStatus;
import moe.hhm.shiori.product.dto.CreateProductRequest;
import moe.hhm.shiori.product.dto.ProductDetailResponse;
import moe.hhm.shiori.product.dto.ProductPageResponse;
import moe.hhm.shiori.product.dto.ProductSummaryResponse;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.SkuInput;
import moe.hhm.shiori.product.dto.SkuResponse;
import moe.hhm.shiori.product.dto.UpdateProductRequest;
import moe.hhm.shiori.product.model.ProductEntity;
import moe.hhm.shiori.product.model.ProductRecord;
import moe.hhm.shiori.product.model.SkuEntity;
import moe.hhm.shiori.product.model.SkuRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import moe.hhm.shiori.product.storage.OssObjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final OssObjectService ossObjectService;

    public ProductService(ProductMapper productMapper, OssObjectService ossObjectService) {
        this.productMapper = productMapper;
        this.ossObjectService = ossObjectService;
    }

    public ProductPageResponse listOnSaleProducts(String keyword, int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        long total = productMapper.countOnSaleProducts(keyword);
        List<ProductRecord> records = productMapper.listOnSaleProducts(keyword, normalizedSize, offset);
        if (records == null) {
            records = List.of();
        }
        List<ProductSummaryResponse> items = records.stream()
                .map(this::toSummaryResponse)
                .toList();
        return new ProductPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public ProductPageResponse listMyProducts(Long ownerUserId, String keyword, String status, int page, int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        Integer statusCode = parseStatusCode(status);
        int offset = (normalizedPage - 1) * normalizedSize;

        long total = productMapper.countProductsByOwner(ownerUserId, keyword, statusCode);
        List<ProductRecord> records = productMapper.listProductsByOwner(
                ownerUserId,
                keyword,
                statusCode,
                normalizedSize,
                offset
        );
        if (records == null) {
            records = List.of();
        }

        List<ProductSummaryResponse> items = records.stream()
                .map(this::toSummaryResponse)
                .toList();
        return new ProductPageResponse(total, normalizedPage, normalizedSize, items);
    }

    public ProductDetailResponse getOnSaleProductDetail(Long productId) {
        ProductRecord product = productMapper.findOnSaleProductById(productId);
        if (product == null) {
            ProductRecord existing = productMapper.findProductById(productId);
            if (existing == null || isDeleted(existing)) {
                throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            throw new BizException(ProductErrorCode.PRODUCT_NOT_ON_SALE, HttpStatus.BAD_REQUEST);
        }
        List<SkuRecord> skus = productMapper.listActiveSkusByProductId(productId);
        return toDetailResponse(product, skus);
    }

    public ProductDetailResponse getMyProductDetail(Long productId, Long userId, boolean admin) {
        ProductRecord product = requireProduct(productId);
        ensureOwnerOrAdmin(product, userId, admin);
        List<SkuRecord> skus = productMapper.listActiveSkusByProductId(productId);
        return toDetailResponse(product, skus);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductWriteResponse createProduct(Long ownerUserId, CreateProductRequest request) {
        assertCoverObjectKey(request.coverObjectKey());

        ProductEntity entity = new ProductEntity();
        entity.setProductNo(generateProductNo());
        entity.setOwnerUserId(ownerUserId);
        entity.setTitle(request.title().trim());
        entity.setDescription(request.description());
        entity.setCoverObjectKey(request.coverObjectKey());
        entity.setStatus(ProductStatus.DRAFT.getCode());
        productMapper.insertProduct(entity);

        if (entity.getId() == null) {
            throw new IllegalStateException("创建商品后未返回主键");
        }
        for (SkuInput input : request.skus()) {
            productMapper.insertSku(toNewSkuEntity(entity.getId(), input));
        }
        return new ProductWriteResponse(entity.getId(), entity.getProductNo(), ProductStatus.DRAFT.name());
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductWriteResponse updateProduct(Long productId, Long userId, boolean admin, UpdateProductRequest request) {
        ProductRecord product = requireProduct(productId);
        ensureOwnerOrAdmin(product, userId, admin);
        assertCoverObjectKey(request.coverObjectKey());

        productMapper.updateProductBase(productId, request.title().trim(), request.description(), request.coverObjectKey());

        List<SkuRecord> existingSkus = productMapper.listActiveSkusByProductId(productId);
        Map<Long, SkuRecord> existingMap = new HashMap<>();
        for (SkuRecord sku : existingSkus) {
            existingMap.put(sku.id(), sku);
        }

        Set<Long> keepSkuIds = new HashSet<>();
        for (SkuInput input : request.skus()) {
            if (input.id() == null) {
                productMapper.insertSku(toNewSkuEntity(productId, input));
                continue;
            }
            SkuRecord existing = existingMap.get(input.id());
            if (existing == null) {
                throw new BizException(ProductErrorCode.SKU_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            SkuEntity updateEntity = new SkuEntity();
            updateEntity.setId(input.id());
            updateEntity.setProductId(productId);
            updateEntity.setSkuName(input.skuName().trim());
            updateEntity.setSpecJson(input.specJson());
            updateEntity.setPriceCent(input.priceCent());
            updateEntity.setStock(input.stock());
            productMapper.updateSku(updateEntity);
            keepSkuIds.add(input.id());
        }

        for (SkuRecord existing : existingSkus) {
            if (!keepSkuIds.contains(existing.id())) {
                productMapper.softDeleteSkuById(existing.id(), productId);
            }
        }

        ProductRecord latest = requireProduct(productId);
        return new ProductWriteResponse(latest.id(), latest.productNo(), ProductStatus.fromCode(latest.status()).name());
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductWriteResponse publishProduct(Long productId, Long userId, boolean admin) {
        ProductRecord product = requireProduct(productId);
        ensureOwnerOrAdmin(product, userId, admin);

        ProductStatus status = ProductStatus.fromCode(product.status());
        if (status == ProductStatus.ON_SALE) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_STATUS, HttpStatus.BAD_REQUEST);
        }
        List<SkuRecord> skus = productMapper.listActiveSkusByProductId(productId);
        if (skus.isEmpty()) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_STATUS, HttpStatus.BAD_REQUEST);
        }
        productMapper.updateProductStatusById(productId, ProductStatus.ON_SALE.getCode());
        return new ProductWriteResponse(productId, product.productNo(), ProductStatus.ON_SALE.name());
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductWriteResponse offShelfProduct(Long productId, Long userId, boolean admin) {
        ProductRecord product = requireProduct(productId);
        ensureOwnerOrAdmin(product, userId, admin);

        ProductStatus status = ProductStatus.fromCode(product.status());
        if (status != ProductStatus.ON_SALE) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_STATUS, HttpStatus.BAD_REQUEST);
        }
        productMapper.updateProductStatusById(productId, ProductStatus.OFF_SHELF.getCode());
        return new ProductWriteResponse(productId, product.productNo(), ProductStatus.OFF_SHELF.name());
    }

    private ProductRecord requireProduct(Long productId) {
        ProductRecord product = productMapper.findProductById(productId);
        if (product == null || isDeleted(product)) {
            throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return product;
    }

    private void ensureOwnerOrAdmin(ProductRecord product, Long userId, boolean admin) {
        if (admin) {
            return;
        }
        if (userId == null || !userId.equals(product.ownerUserId())) {
            throw new BizException(ProductErrorCode.NO_PRODUCT_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    private ProductSummaryResponse toSummaryResponse(ProductRecord record) {
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

    private ProductDetailResponse toDetailResponse(ProductRecord product, List<SkuRecord> skus) {
        List<SkuResponse> skuResponses = new ArrayList<>(skus.size());
        for (SkuRecord sku : skus) {
            skuResponses.add(new SkuResponse(
                    sku.id(),
                    sku.skuNo(),
                    sku.skuName(),
                    sku.specJson(),
                    sku.priceCent(),
                    sku.stock()
            ));
        }
        return new ProductDetailResponse(
                product.id(),
                product.productNo(),
                product.ownerUserId(),
                product.title(),
                product.description(),
                product.coverObjectKey(),
                resolveCoverImageUrl(product.coverObjectKey()),
                ProductStatus.fromCode(product.status()).name(),
                skuResponses
        );
    }

    private SkuEntity toNewSkuEntity(Long productId, SkuInput input) {
        SkuEntity entity = new SkuEntity();
        entity.setProductId(productId);
        entity.setSkuNo(generateSkuNo());
        entity.setSkuName(input.skuName().trim());
        entity.setSpecJson(input.specJson());
        entity.setPriceCent(input.priceCent());
        entity.setStock(input.stock());
        return entity;
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

    private void assertCoverObjectKey(String coverObjectKey) {
        if (!StringUtils.hasText(coverObjectKey)) {
            return;
        }
        if (!coverObjectKey.startsWith("product/") || coverObjectKey.contains("..")) {
            throw new BizException(ProductErrorCode.INVALID_MEDIA_OBJECT_KEY, HttpStatus.BAD_REQUEST);
        }
    }

    private boolean isDeleted(ProductRecord product) {
        return product.isDeleted() != null && product.isDeleted() == 1;
    }

    private String generateProductNo() {
        return "P" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private String generateSkuNo() {
        return "S" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
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
}
