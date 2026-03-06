package moe.hhm.shiori.product.admin.controller.v2;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.admin.dto.AdminProductMetaCampusResponse;
import moe.hhm.shiori.product.admin.dto.AdminProductMetaCategoryResponse;
import moe.hhm.shiori.product.admin.dto.AdminProductMetaSubCategoryResponse;
import moe.hhm.shiori.product.admin.dto.CreateProductCampusRequest;
import moe.hhm.shiori.product.admin.dto.CreateProductCategoryRequest;
import moe.hhm.shiori.product.admin.dto.CreateProductSubCategoryRequest;
import moe.hhm.shiori.product.admin.dto.MigrateProductCampusRequest;
import moe.hhm.shiori.product.admin.dto.MigrateProductSubCategoryRequest;
import moe.hhm.shiori.product.admin.dto.ProductMetaMigrationResponse;
import moe.hhm.shiori.product.admin.dto.UpdateProductCampusRequest;
import moe.hhm.shiori.product.admin.dto.UpdateProductCategoryRequest;
import moe.hhm.shiori.product.admin.dto.UpdateProductSubCategoryRequest;
import moe.hhm.shiori.product.service.ProductMetaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/admin/product-meta")
public class AdminProductMetaV2Controller {

    private final ProductMetaService productMetaService;
    private final PermissionGuard permissionGuard;

    public AdminProductMetaV2Controller(ProductMetaService productMetaService,
                                        PermissionGuard permissionGuard) {
        this.productMetaService = productMetaService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/campuses")
    public List<AdminProductMetaCampusResponse> listCampuses() {
        return productMetaService.listCampusesForAdmin();
    }

    @GetMapping("/categories")
    public List<AdminProductMetaCategoryResponse> listCategories() {
        return productMetaService.listCategoriesForAdmin();
    }

    @PostMapping("/campuses")
    public AdminProductMetaCampusResponse createCampus(@Valid @RequestBody CreateProductCampusRequest request,
                                                       HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.meta.manage", httpServletRequest::getHeader);
        return productMetaService.createCampus(request.campusCode(), request.campusName(), request.sortOrder());
    }

    @PutMapping("/campuses/{campusCode}")
    public AdminProductMetaCampusResponse updateCampus(@PathVariable String campusCode,
                                                       @Valid @RequestBody UpdateProductCampusRequest request,
                                                       HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.meta.manage", httpServletRequest::getHeader);
        return productMetaService.updateCampus(campusCode, request.campusName(), request.status(), request.sortOrder());
    }

    @PostMapping("/categories")
    public AdminProductMetaCategoryResponse createCategory(@Valid @RequestBody CreateProductCategoryRequest request,
                                                           HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.meta.manage", httpServletRequest::getHeader);
        return productMetaService.createCategory(request.categoryCode(), request.categoryName(), request.sortOrder());
    }

    @PutMapping("/categories/{categoryCode}")
    public AdminProductMetaCategoryResponse updateCategory(@PathVariable String categoryCode,
                                                           @Valid @RequestBody UpdateProductCategoryRequest request,
                                                           HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.meta.manage", httpServletRequest::getHeader);
        return productMetaService.updateCategory(categoryCode, request.categoryName(), request.status(), request.sortOrder());
    }

    @PostMapping("/sub-categories")
    public AdminProductMetaSubCategoryResponse createSubCategory(@Valid @RequestBody CreateProductSubCategoryRequest request,
                                                                 HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.meta.manage", httpServletRequest::getHeader);
        return productMetaService.createSubCategory(
                request.categoryCode(),
                request.subCategoryCode(),
                request.subCategoryName(),
                request.sortOrder()
        );
    }

    @PutMapping("/sub-categories/{subCategoryCode}")
    public AdminProductMetaSubCategoryResponse updateSubCategory(@PathVariable String subCategoryCode,
                                                                 @Valid @RequestBody UpdateProductSubCategoryRequest request,
                                                                 HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.meta.manage", httpServletRequest::getHeader);
        return productMetaService.updateSubCategory(
                subCategoryCode,
                request.subCategoryName(),
                request.status(),
                request.sortOrder()
        );
    }

    @PostMapping("/migrations/campuses")
    public ProductMetaMigrationResponse migrateCampus(@Valid @RequestBody MigrateProductCampusRequest request,
                                                      HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.meta.manage", httpServletRequest::getHeader);
        long affected = productMetaService.migrateCampus(request.fromCampusCode(), request.toCampusCode());
        return new ProductMetaMigrationResponse(affected);
    }

    @PostMapping("/migrations/sub-categories")
    public ProductMetaMigrationResponse migrateSubCategory(@Valid @RequestBody MigrateProductSubCategoryRequest request,
                                                           HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.meta.manage", httpServletRequest::getHeader);
        long affected = productMetaService.migrateSubCategory(request.fromSubCategoryCode(), request.toSubCategoryCode());
        return new ProductMetaMigrationResponse(affected);
    }
}
