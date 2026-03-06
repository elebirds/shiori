package moe.hhm.shiori.product.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.admin.dto.AdminProductMetaCampusResponse;
import moe.hhm.shiori.product.admin.dto.AdminProductMetaCategoryResponse;
import moe.hhm.shiori.product.admin.dto.AdminProductMetaSubCategoryResponse;
import moe.hhm.shiori.product.dto.v2.ProductMetaCampusResponse;
import moe.hhm.shiori.product.dto.v2.ProductMetaCategoryResponse;
import moe.hhm.shiori.product.dto.v2.ProductMetaSubCategoryResponse;
import moe.hhm.shiori.product.model.ProductCampusRecord;
import moe.hhm.shiori.product.model.ProductCategoryRecord;
import moe.hhm.shiori.product.model.ProductSubCategoryRecord;
import moe.hhm.shiori.product.repository.ProductMetaMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductMetaService {

    private final ProductMetaMapper productMetaMapper;

    public ProductMetaService(ProductMetaMapper productMetaMapper) {
        this.productMetaMapper = productMetaMapper;
    }

    public List<ProductMetaCampusResponse> listEnabledCampuses() {
        return productMetaMapper.listEnabledCampuses().stream()
                .map(item -> new ProductMetaCampusResponse(item.campusCode(), item.campusName()))
                .toList();
    }

    public List<ProductMetaCategoryResponse> listEnabledCategories() {
        List<ProductCategoryRecord> categories = productMetaMapper.listEnabledCategories();
        List<ProductMetaCategoryResponse> result = new ArrayList<>(categories.size());
        for (ProductCategoryRecord category : categories) {
            List<ProductMetaSubCategoryResponse> subCategories = productMetaMapper
                    .listEnabledSubCategoriesByCategory(category.categoryCode())
                    .stream()
                    .map(item -> new ProductMetaSubCategoryResponse(item.subCategoryCode(), item.subCategoryName()))
                    .toList();
            result.add(new ProductMetaCategoryResponse(category.categoryCode(), category.categoryName(), subCategories));
        }
        return result;
    }

    public List<AdminProductMetaCampusResponse> listCampusesForAdmin() {
        return productMetaMapper.listAllCampuses().stream()
                .map(this::toAdminCampus)
                .toList();
    }

    public List<AdminProductMetaCategoryResponse> listCategoriesForAdmin() {
        List<ProductCategoryRecord> categories = productMetaMapper.listAllCategories();
        List<ProductSubCategoryRecord> allSubCategories = productMetaMapper.listAllSubCategories();
        Map<String, List<AdminProductMetaSubCategoryResponse>> grouped = new HashMap<>();
        for (ProductSubCategoryRecord subCategory : allSubCategories) {
            grouped.computeIfAbsent(subCategory.categoryCode(), key -> new ArrayList<>())
                    .add(toAdminSubCategory(subCategory));
        }
        List<AdminProductMetaCategoryResponse> result = new ArrayList<>(categories.size());
        for (ProductCategoryRecord category : categories) {
            result.add(new AdminProductMetaCategoryResponse(
                    category.id(),
                    category.categoryCode(),
                    category.categoryName(),
                    category.status(),
                    category.sortOrder(),
                    grouped.getOrDefault(category.categoryCode(), List.of())
            ));
        }
        return result;
    }

    public AdminProductMetaCampusResponse createCampus(String rawCampusCode, String rawCampusName, Integer sortOrder) {
        String campusCode = normalizeCampusCode(rawCampusCode, true);
        String campusName = normalizeName(rawCampusName, 128, CommonErrorCode.INVALID_PARAM);
        int normalizedSortOrder = normalizeSortOrder(sortOrder);

        ProductCampusRecord existed = productMetaMapper.findCampusByCode(campusCode);
        if (existed != null && (existed.isDeleted() == null || existed.isDeleted() == 0)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        productMetaMapper.insertCampus(campusCode, campusName, normalizedSortOrder);
        ProductCampusRecord created = productMetaMapper.findCampusByCode(campusCode);
        if (created == null) {
            throw new IllegalStateException("创建校区后未查到记录");
        }
        return toAdminCampus(created);
    }

    public AdminProductMetaCampusResponse updateCampus(String rawCampusCode,
                                                       String rawCampusName,
                                                       Integer status,
                                                       Integer sortOrder) {
        String campusCode = normalizeCampusCode(rawCampusCode, true);
        String campusName = normalizeName(rawCampusName, 128, CommonErrorCode.INVALID_PARAM);
        int normalizedStatus = normalizeStatus(status);
        int normalizedSortOrder = normalizeSortOrder(sortOrder);
        ProductCampusRecord existed = productMetaMapper.findCampusByCode(campusCode);
        if (existed == null || (existed.isDeleted() != null && existed.isDeleted() == 1)) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_CAMPUS_CODE, HttpStatus.BAD_REQUEST);
        }
        productMetaMapper.updateCampus(campusCode, campusName, normalizedStatus, normalizedSortOrder);
        ProductCampusRecord updated = productMetaMapper.findCampusByCode(campusCode);
        if (updated == null) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_CAMPUS_CODE, HttpStatus.BAD_REQUEST);
        }
        return toAdminCampus(updated);
    }

    public AdminProductMetaCategoryResponse createCategory(String rawCategoryCode, String rawCategoryName, Integer sortOrder) {
        String categoryCode = normalizeCategoryCode(rawCategoryCode, true);
        String categoryName = normalizeName(rawCategoryName, 128, CommonErrorCode.INVALID_PARAM);
        int normalizedSortOrder = normalizeSortOrder(sortOrder);

        ProductCategoryRecord existed = productMetaMapper.findCategoryByCode(categoryCode);
        if (existed != null && (existed.isDeleted() == null || existed.isDeleted() == 0)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        productMetaMapper.insertCategory(categoryCode, categoryName, normalizedSortOrder);
        ProductCategoryRecord created = productMetaMapper.findCategoryByCode(categoryCode);
        if (created == null) {
            throw new IllegalStateException("创建分类后未查到记录");
        }
        return new AdminProductMetaCategoryResponse(
                created.id(),
                created.categoryCode(),
                created.categoryName(),
                created.status(),
                created.sortOrder(),
                List.of()
        );
    }

    public AdminProductMetaCategoryResponse updateCategory(String rawCategoryCode,
                                                           String rawCategoryName,
                                                           Integer status,
                                                           Integer sortOrder) {
        String categoryCode = normalizeCategoryCode(rawCategoryCode, true);
        String categoryName = normalizeName(rawCategoryName, 128, CommonErrorCode.INVALID_PARAM);
        int normalizedStatus = normalizeStatus(status);
        int normalizedSortOrder = normalizeSortOrder(sortOrder);

        ProductCategoryRecord existed = productMetaMapper.findCategoryByCode(categoryCode);
        if (existed == null || (existed.isDeleted() != null && existed.isDeleted() == 1)) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_CATEGORY, HttpStatus.BAD_REQUEST);
        }
        productMetaMapper.updateCategory(categoryCode, categoryName, normalizedStatus, normalizedSortOrder);
        ProductCategoryRecord updated = productMetaMapper.findCategoryByCode(categoryCode);
        if (updated == null) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_CATEGORY, HttpStatus.BAD_REQUEST);
        }
        List<AdminProductMetaSubCategoryResponse> subCategories = productMetaMapper.listAllSubCategories().stream()
                .filter(item -> Objects.equals(item.categoryCode(), categoryCode))
                .map(this::toAdminSubCategory)
                .toList();
        return new AdminProductMetaCategoryResponse(
                updated.id(),
                updated.categoryCode(),
                updated.categoryName(),
                updated.status(),
                updated.sortOrder(),
                subCategories
        );
    }

    public AdminProductMetaSubCategoryResponse createSubCategory(String rawCategoryCode,
                                                                 String rawSubCategoryCode,
                                                                 String rawSubCategoryName,
                                                                 Integer sortOrder) {
        String categoryCode = normalizeCategoryCode(rawCategoryCode, true);
        String subCategoryCode = normalizeSubCategoryCode(rawSubCategoryCode, true);
        String subCategoryName = normalizeName(rawSubCategoryName, 128, CommonErrorCode.INVALID_PARAM);
        int normalizedSortOrder = normalizeSortOrder(sortOrder);

        ProductCategoryRecord category = productMetaMapper.findCategoryByCode(categoryCode);
        if (category == null || (category.isDeleted() != null && category.isDeleted() == 1)) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_CATEGORY, HttpStatus.BAD_REQUEST);
        }

        ProductSubCategoryRecord existed = productMetaMapper.findSubCategoryByCode(subCategoryCode);
        if (existed != null && (existed.isDeleted() == null || existed.isDeleted() == 0)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }

        productMetaMapper.insertSubCategory(categoryCode, subCategoryCode, subCategoryName, normalizedSortOrder);
        ProductSubCategoryRecord created = productMetaMapper.findSubCategoryByCode(subCategoryCode);
        if (created == null) {
            throw new IllegalStateException("创建子分类后未查到记录");
        }
        return toAdminSubCategory(created);
    }

    public AdminProductMetaSubCategoryResponse updateSubCategory(String rawSubCategoryCode,
                                                                 String rawSubCategoryName,
                                                                 Integer status,
                                                                 Integer sortOrder) {
        String subCategoryCode = normalizeSubCategoryCode(rawSubCategoryCode, true);
        String subCategoryName = normalizeName(rawSubCategoryName, 128, CommonErrorCode.INVALID_PARAM);
        int normalizedStatus = normalizeStatus(status);
        int normalizedSortOrder = normalizeSortOrder(sortOrder);

        ProductSubCategoryRecord existed = productMetaMapper.findSubCategoryByCode(subCategoryCode);
        if (existed == null || (existed.isDeleted() != null && existed.isDeleted() == 1)) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_SUB_CATEGORY, HttpStatus.BAD_REQUEST);
        }
        productMetaMapper.updateSubCategory(subCategoryCode, subCategoryName, normalizedStatus, normalizedSortOrder);
        ProductSubCategoryRecord updated = productMetaMapper.findSubCategoryByCode(subCategoryCode);
        if (updated == null) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_SUB_CATEGORY, HttpStatus.BAD_REQUEST);
        }
        return toAdminSubCategory(updated);
    }

    public long migrateCampus(String rawFromCampusCode, String rawToCampusCode) {
        String fromCampusCode = normalizeCampusCode(rawFromCampusCode, true);
        String toCampusCode = normalizeCampusCode(rawToCampusCode, true);
        if (Objects.equals(fromCampusCode, toCampusCode)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        requireEnabledCampus(toCampusCode);
        return productMetaMapper.migrateProductCampus(fromCampusCode, toCampusCode);
    }

    public long migrateSubCategory(String rawFromSubCategoryCode, String rawToSubCategoryCode) {
        String fromSubCategoryCode = normalizeSubCategoryCode(rawFromSubCategoryCode, true);
        String toSubCategoryCode = normalizeSubCategoryCode(rawToSubCategoryCode, true);
        if (Objects.equals(fromSubCategoryCode, toSubCategoryCode)) {
            throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
        }
        ProductSubCategoryRecord toSubCategory = requireEnabledSubCategory(toSubCategoryCode);
        return productMetaMapper.migrateProductSubCategory(
                fromSubCategoryCode,
                toSubCategoryCode,
                toSubCategory.categoryCode()
        );
    }

    public void ensureSelectionEnabledForCreate(String categoryCode, String subCategoryCode, String campusCode) {
        requireEnabledCategory(categoryCode);
        ProductSubCategoryRecord subCategory = requireEnabledSubCategory(subCategoryCode);
        if (!Objects.equals(subCategory.categoryCode(), categoryCode)) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_SUB_CATEGORY, HttpStatus.BAD_REQUEST);
        }
        requireEnabledCampus(campusCode);
    }

    public void ensureSelectionAllowedForUpdate(String oldCategoryCode,
                                                String oldSubCategoryCode,
                                                String oldCampusCode,
                                                String newCategoryCode,
                                                String newSubCategoryCode,
                                                String newCampusCode) {
        boolean categoryChanged = !Objects.equals(oldCategoryCode, newCategoryCode);
        boolean subCategoryChanged = !Objects.equals(oldSubCategoryCode, newSubCategoryCode);
        boolean campusChanged = !Objects.equals(oldCampusCode, newCampusCode);

        if (categoryChanged || subCategoryChanged) {
            requireEnabledCategory(newCategoryCode);
            ProductSubCategoryRecord subCategory = requireEnabledSubCategory(newSubCategoryCode);
            if (!Objects.equals(subCategory.categoryCode(), newCategoryCode)) {
                throw new BizException(ProductErrorCode.INVALID_PRODUCT_SUB_CATEGORY, HttpStatus.BAD_REQUEST);
            }
        }
        if (campusChanged) {
            requireEnabledCampus(newCampusCode);
        }
    }

    private ProductCampusRecord requireEnabledCampus(String campusCode) {
        ProductCampusRecord record = productMetaMapper.findCampusByCode(campusCode);
        if (record == null || (record.isDeleted() != null && record.isDeleted() == 1) || record.status() == null
                || record.status() != 1) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_CAMPUS_CODE, HttpStatus.BAD_REQUEST);
        }
        return record;
    }

    private ProductCategoryRecord requireEnabledCategory(String categoryCode) {
        ProductCategoryRecord record = productMetaMapper.findCategoryByCode(categoryCode);
        if (record == null || (record.isDeleted() != null && record.isDeleted() == 1) || record.status() == null
                || record.status() != 1) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_CATEGORY, HttpStatus.BAD_REQUEST);
        }
        return record;
    }

    private ProductSubCategoryRecord requireEnabledSubCategory(String subCategoryCode) {
        ProductSubCategoryRecord record = productMetaMapper.findSubCategoryByCode(subCategoryCode);
        if (record == null || (record.isDeleted() != null && record.isDeleted() == 1) || record.status() == null
                || record.status() != 1) {
            throw new BizException(ProductErrorCode.INVALID_PRODUCT_SUB_CATEGORY, HttpStatus.BAD_REQUEST);
        }
        return record;
    }

    private AdminProductMetaCampusResponse toAdminCampus(ProductCampusRecord record) {
        return new AdminProductMetaCampusResponse(
                record.id(),
                record.campusCode(),
                record.campusName(),
                record.status(),
                record.sortOrder()
        );
    }

    private AdminProductMetaSubCategoryResponse toAdminSubCategory(ProductSubCategoryRecord record) {
        return new AdminProductMetaSubCategoryResponse(
                record.id(),
                record.categoryCode(),
                record.subCategoryCode(),
                record.subCategoryName(),
                record.status(),
                record.sortOrder()
        );
    }

    private int normalizeStatus(Integer rawStatus) {
        if (rawStatus == null) {
            return 1;
        }
        if (rawStatus == 0 || rawStatus == 1) {
            return rawStatus;
        }
        throw new BizException(CommonErrorCode.INVALID_PARAM, HttpStatus.BAD_REQUEST);
    }

    private int normalizeSortOrder(Integer rawSortOrder) {
        if (rawSortOrder == null) {
            return 0;
        }
        return rawSortOrder;
    }

    private String normalizeCategoryCode(String raw, boolean required) {
        return normalizeCode(raw, required, 64, ProductErrorCode.INVALID_PRODUCT_CATEGORY, "^[A-Z0-9_]+$");
    }

    private String normalizeSubCategoryCode(String raw, boolean required) {
        return normalizeCode(raw, required, 64, ProductErrorCode.INVALID_PRODUCT_SUB_CATEGORY, "^[A-Z0-9_]+$");
    }

    private String normalizeCampusCode(String raw, boolean required) {
        return normalizeCode(raw, required, 64, ProductErrorCode.INVALID_PRODUCT_CAMPUS_CODE, "^[A-Za-z0-9_-]+$");
    }

    private String normalizeCode(String raw,
                                 boolean required,
                                 int maxLength,
                                 ProductErrorCode errorCode,
                                 String pattern) {
        if (!StringUtils.hasText(raw)) {
            if (required) {
                throw new BizException(errorCode, HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = raw.trim();
        if (errorCode == ProductErrorCode.INVALID_PRODUCT_CATEGORY
                || errorCode == ProductErrorCode.INVALID_PRODUCT_SUB_CATEGORY) {
            normalized = normalized.toUpperCase();
        }
        if (normalized.length() > maxLength || !normalized.matches(pattern)) {
            throw new BizException(errorCode, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeName(String raw, int maxLength, CommonErrorCode errorCode) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(errorCode, HttpStatus.BAD_REQUEST);
        }
        String normalized = raw.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(errorCode, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }
}
