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
import moe.hhm.shiori.common.richtext.RichTextPolicies;
import moe.hhm.shiori.common.richtext.RichTextProcessor;
import moe.hhm.shiori.product.domain.ProductConditionLevel;
import moe.hhm.shiori.product.domain.ProductStatus;
import moe.hhm.shiori.product.domain.ProductTradeMode;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.SpecItemResponse;
import moe.hhm.shiori.product.dto.SkuInput;
import moe.hhm.shiori.product.dto.SkuResponse;
import moe.hhm.shiori.product.dto.v2.CreateProductV2Request;
import moe.hhm.shiori.product.dto.v2.ProductV2DetailResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2PageResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2SummaryResponse;
import moe.hhm.shiori.product.dto.v2.UpdateProductV2Request;
import moe.hhm.shiori.product.model.ProductEntity;
import moe.hhm.shiori.product.model.ProductRecord;
import moe.hhm.shiori.product.model.ProductV2Record;
import moe.hhm.shiori.product.model.SkuEntity;
import moe.hhm.shiori.product.model.SkuRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import moe.hhm.shiori.common.storage.OssObjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductV2Service {

    private static final RichTextProcessor DETAIL_HTML_PROCESSOR =
            new RichTextProcessor(RichTextPolicies.productMediaPolicy());

    private final ProductMapper productMapper;
    private final OssObjectService ossObjectService;
    private final ProductService productService;
    private final ProductMetrics productMetrics;
    private final SkuSpecCodec skuSpecCodec;
    private final ProductMetaService productMetaService;

    public ProductV2Service(ProductMapper productMapper,
                            OssObjectService ossObjectService,
                            ProductService productService,
                            ProductMetrics productMetrics,
                            SkuSpecCodec skuSpecCodec,
                            ProductMetaService productMetaService) {
        this.productMapper = productMapper;
        this.ossObjectService = ossObjectService;
        this.productService = productService;
        this.productMetrics = productMetrics;
        this.skuSpecCodec = skuSpecCodec;
        this.productMetaService = productMetaService;
    }

    public ProductV2PageResponse listOnSaleProducts(String keyword,
                                                    String categoryCode,
                                                    String subCategoryCode,
                                                    String conditionLevel,
                                                    String tradeMode,
                                                    String campusCode,
                                                    String sortBy,
                                                    String sortDir,
                                                    int page,
                                                    int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;

        String normalizedCategory = normalizeCategory(categoryCode, false);
        String normalizedSubCategory = normalizeSubCategory(subCategoryCode, false);
        String normalizedCondition = normalizeCondition(conditionLevel, false);
        String normalizedTradeMode = normalizeTradeMode(tradeMode, false);
        String normalizedCampusCode = normalizeCampusCode(campusCode, false);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);

        long total = productMapper.countOnSaleProductsV2(keyword, normalizedCategory, normalizedSubCategory,
                normalizedCondition, normalizedTradeMode, normalizedCampusCode);
        List<ProductV2Record> records = productMapper.listOnSaleProductsV2(keyword, normalizedCategory,
                normalizedSubCategory, normalizedCondition, normalizedTradeMode, normalizedCampusCode,
                normalizedSortBy, normalizedSortDir, normalizedSize, offset);
        if (records == null) {
            records = List.of();
        }
        List<ProductV2SummaryResponse> items = records.stream().map(this::toSummaryResponse).toList();
        productMetrics.incQuery(buildFilterCombo(keyword, normalizedCategory, normalizedSubCategory,
                normalizedCondition, normalizedTradeMode, normalizedCampusCode, normalizedSortBy));
        return new ProductV2PageResponse(total, normalizedPage, normalizedSize, items);
    }

    public ProductV2PageResponse listMyProducts(Long ownerUserId,
                                                String keyword,
                                                String status,
                                                String categoryCode,
                                                String subCategoryCode,
                                                String conditionLevel,
                                                String tradeMode,
                                                String campusCode,
                                                String sortBy,
                                                String sortDir,
                                                int page,
                                                int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        Integer statusCode = parseStatusCode(status);
        String normalizedCategory = normalizeCategory(categoryCode, false);
        String normalizedSubCategory = normalizeSubCategory(subCategoryCode, false);
        String normalizedCondition = normalizeCondition(conditionLevel, false);
        String normalizedTradeMode = normalizeTradeMode(tradeMode, false);
        String normalizedCampusCode = normalizeCampusCode(campusCode, false);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);

        long total = productMapper.countProductsByOwnerV2(ownerUserId, keyword, statusCode, normalizedCategory,
                normalizedSubCategory, normalizedCondition, normalizedTradeMode, normalizedCampusCode);
        List<ProductV2Record> records = productMapper.listProductsByOwnerV2(ownerUserId, keyword, statusCode,
                normalizedCategory, normalizedSubCategory, normalizedCondition, normalizedTradeMode,
                normalizedCampusCode,
                normalizedSortBy, normalizedSortDir, normalizedSize, offset);
        if (records == null) {
            records = List.of();
        }

        List<ProductV2SummaryResponse> items = records.stream().map(this::toSummaryResponse).toList();
        productMetrics.incQuery(buildFilterCombo(keyword, normalizedCategory, normalizedSubCategory,
                normalizedCondition, normalizedTradeMode, normalizedCampusCode, normalizedSortBy));
        return new ProductV2PageResponse(total, normalizedPage, normalizedSize, items);
    }

    public ProductV2PageResponse listOnSaleProductsByOwner(Long ownerUserId,
                                                            String keyword,
                                                            String categoryCode,
                                                            String subCategoryCode,
                                                            String conditionLevel,
                                                            String tradeMode,
                                                            String campusCode,
                                                            String sortBy,
                                                            String sortDir,
                                                            int page,
                                                            int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedCategory = normalizeCategory(categoryCode, false);
        String normalizedSubCategory = normalizeSubCategory(subCategoryCode, false);
        String normalizedCondition = normalizeCondition(conditionLevel, false);
        String normalizedTradeMode = normalizeTradeMode(tradeMode, false);
        String normalizedCampusCode = normalizeCampusCode(campusCode, false);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);
        int onSaleStatus = ProductStatus.ON_SALE.getCode();

        long total = productMapper.countProductsByOwnerV2(ownerUserId, keyword, onSaleStatus, normalizedCategory,
                normalizedSubCategory, normalizedCondition, normalizedTradeMode, normalizedCampusCode);
        List<ProductV2Record> records = productMapper.listProductsByOwnerV2(ownerUserId, keyword, onSaleStatus,
                normalizedCategory, normalizedSubCategory, normalizedCondition, normalizedTradeMode,
                normalizedCampusCode,
                normalizedSortBy, normalizedSortDir, normalizedSize, offset);
        if (records == null) {
            records = List.of();
        }
        List<ProductV2SummaryResponse> items = records.stream().map(this::toSummaryResponse).toList();
        productMetrics.incQuery(buildFilterCombo(keyword, normalizedCategory, normalizedSubCategory,
                normalizedCondition, normalizedTradeMode, normalizedCampusCode, normalizedSortBy));
        return new ProductV2PageResponse(total, normalizedPage, normalizedSize, items);
    }

    public ProductV2PageResponse listProductsForAdmin(String keyword,
                                                      String status,
                                                      Long ownerUserId,
                                                      String categoryCode,
                                                      String subCategoryCode,
                                                      String conditionLevel,
                                                      String tradeMode,
                                                      String campusCode,
                                                      String sortBy,
                                                      String sortDir,
                                                      int page,
                                                      int size) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        int offset = (normalizedPage - 1) * normalizedSize;
        Integer statusCode = parseStatusCode(status);
        String normalizedCategory = normalizeCategory(categoryCode, false);
        String normalizedSubCategory = normalizeSubCategory(subCategoryCode, false);
        String normalizedCondition = normalizeCondition(conditionLevel, false);
        String normalizedTradeMode = normalizeTradeMode(tradeMode, false);
        String normalizedCampusCode = normalizeCampusCode(campusCode, false);
        String normalizedSortBy = normalizeSortBy(sortBy);
        String normalizedSortDir = normalizeSortDir(sortDir);

        long total = productMapper.countProductsForAdminV2(keyword, statusCode, ownerUserId, normalizedCategory,
                normalizedSubCategory, normalizedCondition, normalizedTradeMode, normalizedCampusCode);
        List<ProductV2Record> records = productMapper.listProductsForAdminV2(keyword, statusCode, ownerUserId,
                normalizedCategory, normalizedSubCategory, normalizedCondition, normalizedTradeMode,
                normalizedCampusCode,
                normalizedSortBy, normalizedSortDir, normalizedSize, offset);
        if (records == null) {
            records = List.of();
        }
        List<ProductV2SummaryResponse> items = records.stream().map(this::toSummaryResponse).toList();
        productMetrics.incQuery(buildFilterCombo(keyword, normalizedCategory, normalizedSubCategory,
                normalizedCondition, normalizedTradeMode, normalizedCampusCode, normalizedSortBy));
        return new ProductV2PageResponse(total, normalizedPage, normalizedSize, items);
    }

    public ProductV2DetailResponse getOnSaleProductDetail(Long productId) {
        ProductV2Record product = productMapper.findOnSaleProductV2ById(productId);
        if (product == null) {
            ProductV2Record existing = productMapper.findProductV2ById(productId);
            if (existing == null || isDeleted(existing)) {
                throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            throw new BizException(ProductErrorCode.PRODUCT_NOT_ON_SALE, HttpStatus.BAD_REQUEST);
        }
        List<SkuRecord> skus = productMapper.listActiveSkusByProductId(productId);
        return toDetailResponse(product, skus);
    }

    public ProductV2DetailResponse getMyProductDetail(Long productId, Long userId, boolean admin) {
        ProductV2Record product = requireProductV2(productId);
        ensureOwnerOrAdmin(product.ownerUserId(), userId, admin);
        List<SkuRecord> skus = productMapper.listActiveSkusByProductId(productId);
        return toDetailResponse(product, skus);
    }

    public ProductV2DetailResponse getProductDetailForAdmin(Long productId) {
        ProductV2Record product = requireProductV2(productId);
        List<SkuRecord> skus = productMapper.listActiveSkusByProductId(productId);
        return toDetailResponse(product, skus);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductWriteResponse createProduct(Long ownerUserId, CreateProductV2Request request) {
        assertCoverObjectKey(request.coverObjectKey());
        String normalizedDetailHtml = sanitizeDetailHtmlForStore(request.detailHtml());
        String categoryCode = normalizeCategory(request.categoryCode(), true);
        String subCategoryCode = normalizeSubCategory(request.subCategoryCode(), true);
        String conditionLevel = normalizeCondition(request.conditionLevel(), true);
        String tradeMode = normalizeTradeMode(request.tradeMode(), true);
        String campusCode = normalizeCampusCode(request.campusCode(), true);
        productMetaService.ensureSelectionEnabledForCreate(categoryCode, subCategoryCode, campusCode);

        ProductEntity entity = new ProductEntity();
        entity.setProductNo(generateProductNo());
        entity.setOwnerUserId(ownerUserId);
        entity.setTitle(request.title().trim());
        entity.setDescription(request.description());
        entity.setDetailHtml(normalizedDetailHtml);
        entity.setCoverObjectKey(request.coverObjectKey());
        entity.setCategoryCode(categoryCode);
        entity.setSubCategoryCode(subCategoryCode);
        entity.setConditionLevel(conditionLevel);
        entity.setTradeMode(tradeMode);
        entity.setCampusCode(campusCode);
        entity.setStatus(ProductStatus.DRAFT.getCode());
        productMapper.insertProduct(entity);

        if (entity.getId() == null) {
            throw new IllegalStateException("创建商品后未返回主键");
        }
        Set<String> signatures = new HashSet<>();
        for (SkuInput input : request.skus()) {
            List<SpecItemResponse> specItems = skuSpecCodec.normalizeInput(input.specItems());
            String signature = skuSpecCodec.toSpecSignature(specItems);
            if (!signatures.add(signature)) {
                throw new BizException(ProductErrorCode.DUPLICATE_SKU_SPEC_COMBINATION, HttpStatus.BAD_REQUEST);
            }
            productMapper.insertSku(toNewSkuEntity(entity.getId(), input, specItems, signature));
        }
        return new ProductWriteResponse(entity.getId(), entity.getProductNo(), ProductStatus.DRAFT.name());
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductWriteResponse updateProduct(Long productId, Long userId, boolean admin, UpdateProductV2Request request) {
        ProductV2Record product = requireProductV2(productId);
        ensureOwnerOrAdmin(product.ownerUserId(), userId, admin);
        assertCoverObjectKey(request.coverObjectKey());
        String normalizedDetailHtml = sanitizeDetailHtmlForStore(request.detailHtml());

        String categoryCode = normalizeCategory(request.categoryCode(), true);
        String subCategoryCode = normalizeSubCategory(request.subCategoryCode(), true);
        String conditionLevel = normalizeCondition(request.conditionLevel(), true);
        String tradeMode = normalizeTradeMode(request.tradeMode(), true);
        String campusCode = normalizeCampusCode(request.campusCode(), true);
        productMetaService.ensureSelectionAllowedForUpdate(
                product.categoryCode(),
                product.subCategoryCode(),
                product.campusCode(),
                categoryCode,
                subCategoryCode,
                campusCode
        );

        productMapper.updateProductBase(
                productId,
                request.title().trim(),
                request.description(),
                normalizedDetailHtml,
                request.coverObjectKey(),
                categoryCode,
                subCategoryCode,
                conditionLevel,
                tradeMode,
                campusCode
        );

        List<SkuRecord> existingSkus = productMapper.listActiveSkusByProductId(productId);
        Map<Long, SkuRecord> existingMap = new HashMap<>();
        for (SkuRecord sku : existingSkus) {
            existingMap.put(sku.id(), sku);
        }

        Set<Long> keepSkuIds = new HashSet<>();
        Set<String> requestSignatures = new HashSet<>();
        for (SkuInput input : request.skus()) {
            List<SpecItemResponse> specItems = skuSpecCodec.normalizeInput(input.specItems());
            String signature = skuSpecCodec.toSpecSignature(specItems);
            if (!requestSignatures.add(signature)) {
                throw new BizException(ProductErrorCode.DUPLICATE_SKU_SPEC_COMBINATION, HttpStatus.BAD_REQUEST);
            }
            if (input.id() == null) {
                productMapper.insertSku(toNewSkuEntity(productId, input, specItems, signature));
                continue;
            }
            SkuRecord existing = existingMap.get(input.id());
            if (existing == null) {
                throw new BizException(ProductErrorCode.SKU_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            SkuEntity updateEntity = new SkuEntity();
            updateEntity.setId(input.id());
            updateEntity.setProductId(productId);
            String displayName = skuSpecCodec.toDisplayName(specItems);
            updateEntity.setDisplayName(displayName);
            updateEntity.setSpecItemsJson(skuSpecCodec.toSpecItemsJson(specItems));
            updateEntity.setSpecSignature(signature);
            updateEntity.setSkuName(displayName);
            updateEntity.setSpecJson(skuSpecCodec.toLegacySpecJson(specItems));
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

        ProductRecord latest = productMapper.findProductById(productId);
        if (latest == null) {
            throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return new ProductWriteResponse(latest.id(), latest.productNo(), ProductStatus.fromCode(latest.status()).name());
    }

    public ProductWriteResponse publishProduct(Long productId, Long userId, boolean admin) {
        return productService.publishProduct(productId, userId, admin);
    }

    public ProductWriteResponse offShelfProduct(Long productId, Long userId, boolean admin) {
        return productService.offShelfProduct(productId, userId, admin);
    }

    private ProductV2Record requireProductV2(Long productId) {
        ProductV2Record product = productMapper.findProductV2ById(productId);
        if (product == null || isDeleted(product)) {
            throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return product;
    }

    private void ensureOwnerOrAdmin(Long ownerUserId, Long userId, boolean admin) {
        if (admin) {
            return;
        }
        if (userId == null || !userId.equals(ownerUserId)) {
            throw new BizException(ProductErrorCode.NO_PRODUCT_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    private ProductV2SummaryResponse toSummaryResponse(ProductV2Record record) {
        return new ProductV2SummaryResponse(
                record.id(),
                record.productNo(),
                record.title(),
                record.description(),
                record.coverObjectKey(),
                resolveCoverImageUrl(record.coverObjectKey()),
                ProductStatus.fromCode(record.status()).name(),
                record.categoryCode(),
                record.subCategoryCode(),
                record.conditionLevel(),
                record.tradeMode(),
                record.campusCode(),
                record.minPriceCent(),
                record.maxPriceCent(),
                record.totalStock()
        );
    }

    private ProductV2DetailResponse toDetailResponse(ProductV2Record product, List<SkuRecord> skus) {
        List<SkuResponse> skuResponses = new ArrayList<>(skus.size());
        for (SkuRecord sku : skus) {
            List<SpecItemResponse> specItems = skuSpecCodec.fromSkuRecord(sku);
            skuResponses.add(new SkuResponse(
                    sku.id(),
                    sku.skuNo(),
                    StringUtils.hasText(sku.displayName()) ? sku.displayName() : skuSpecCodec.toDisplayName(specItems),
                    specItems,
                    sku.priceCent(),
                    sku.stock()
            ));
        }
        return new ProductV2DetailResponse(
                product.id(),
                product.productNo(),
                product.ownerUserId(),
                product.title(),
                product.description(),
                renderDetailHtmlForResponse(product.detailHtml()),
                product.coverObjectKey(),
                resolveCoverImageUrl(product.coverObjectKey()),
                ProductStatus.fromCode(product.status()).name(),
                product.categoryCode(),
                product.subCategoryCode(),
                product.conditionLevel(),
                product.tradeMode(),
                product.campusCode(),
                product.minPriceCent(),
                product.maxPriceCent(),
                product.totalStock(),
                skuResponses
        );
    }

    private SkuEntity toNewSkuEntity(Long productId,
                                     SkuInput input,
                                     List<SpecItemResponse> specItems,
                                     String signature) {
        SkuEntity entity = new SkuEntity();
        entity.setProductId(productId);
        entity.setSkuNo(generateSkuNo());
        String displayName = skuSpecCodec.toDisplayName(specItems);
        entity.setDisplayName(displayName);
        entity.setSpecItemsJson(skuSpecCodec.toSpecItemsJson(specItems));
        entity.setSpecSignature(signature);
        entity.setSkuName(displayName);
        entity.setSpecJson(skuSpecCodec.toLegacySpecJson(specItems));
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

    private String sanitizeDetailHtmlForStore(String rawDetailHtml) {
        return DETAIL_HTML_PROCESSOR.sanitizeForStore(rawDetailHtml);
    }

    private String renderDetailHtmlForResponse(String storedDetailHtml) {
        return DETAIL_HTML_PROCESSOR.renderForResponse(storedDetailHtml, ossObjectService::presignGetUrl);
    }

    private boolean isDeleted(ProductV2Record product) {
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

    private String normalizeCategory(String raw, boolean required) {
        if (!StringUtils.hasText(raw)) {
            if (required) {
                throw new BizException(ProductErrorCode.INVALID_PRODUCT_CATEGORY, HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = normalizeCode(raw, 64);
        if (normalized == null) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_CATEGORY, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeSubCategory(String raw, boolean required) {
        if (!StringUtils.hasText(raw)) {
            if (required) {
                throw new BizException(ProductErrorCode.INVALID_PRODUCT_SUB_CATEGORY, HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = normalizeCode(raw, 64);
        if (normalized == null) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_SUB_CATEGORY, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeCondition(String raw, boolean required) {
        if (!StringUtils.hasText(raw)) {
            if (required) {
                throw new BizException(ProductErrorCode.INVALID_PRODUCT_CONDITION, HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = ProductConditionLevel.normalize(raw);
        if (normalized == null) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_CONDITION, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeCode(String raw, int maxLength) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toUpperCase();
        if (normalized.length() > maxLength || !normalized.matches("^[A-Z0-9_]+$")) {
            return null;
        }
        return normalized;
    }

    private String normalizeTradeMode(String raw, boolean required) {
        if (!StringUtils.hasText(raw)) {
            if (required) {
                throw new BizException(ProductErrorCode.INVALID_PRODUCT_TRADE_MODE, HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = ProductTradeMode.normalize(raw);
        if (normalized == null) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_TRADE_MODE, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeCampusCode(String raw, boolean required) {
        if (!StringUtils.hasText(raw)) {
            if (required) {
                throw new BizException(ProductErrorCode.INVALID_PRODUCT_CAMPUS_CODE, HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = raw.trim();
        if (normalized.length() > 64 || !normalized.matches("^[A-Za-z0-9_-]+$")) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_CAMPUS_CODE, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeSortBy(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "CREATED_AT";
        }
        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "CREATED_AT", "MIN_PRICE", "MAX_PRICE" -> normalized;
            default -> throw new BizException(ProductErrorCode.INVALID_PRODUCT_SORT, HttpStatus.BAD_REQUEST);
        };
    }

    private String normalizeSortDir(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "DESC";
        }
        String normalized = raw.trim().toUpperCase();
        if (!"ASC".equals(normalized) && !"DESC".equals(normalized)) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_SORT, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String buildFilterCombo(String keyword,
                                    String categoryCode,
                                    String subCategoryCode,
                                    String conditionLevel,
                                    String tradeMode,
                                    String campusCode,
                                    String sortBy) {
        List<String> flags = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            flags.add("keyword");
        }
        if (StringUtils.hasText(categoryCode)) {
            flags.add("category");
        }
        if (StringUtils.hasText(subCategoryCode)) {
            flags.add("subCategory");
        }
        if (StringUtils.hasText(conditionLevel)) {
            flags.add("condition");
        }
        if (StringUtils.hasText(tradeMode)) {
            flags.add("trade");
        }
        if (StringUtils.hasText(campusCode)) {
            flags.add("campus");
        }
        if (StringUtils.hasText(sortBy) && !"CREATED_AT".equals(sortBy)) {
            flags.add("sort:" + sortBy);
        }
        if (flags.isEmpty()) {
            return "none";
        }
        return String.join("+", flags);
    }
}
