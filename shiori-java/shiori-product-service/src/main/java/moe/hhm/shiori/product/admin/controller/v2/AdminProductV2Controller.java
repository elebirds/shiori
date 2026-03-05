package moe.hhm.shiori.product.admin.controller.v2;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.servlet.http.HttpServletRequest;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.admin.dto.AdminProductOffShelfRequest;
import moe.hhm.shiori.product.admin.service.AdminProductService;
import moe.hhm.shiori.product.admin.service.AdminProductV2Service;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2BatchOffShelfRequest;
import moe.hhm.shiori.product.dto.v2.ProductV2BatchOffShelfResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2DetailResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2PageResponse;
import moe.hhm.shiori.product.security.CurrentUserSupport;
import moe.hhm.shiori.product.service.ProductV2Service;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2/admin/products")
public class AdminProductV2Controller {

    private final ProductV2Service productV2Service;
    private final AdminProductService adminProductService;
    private final AdminProductV2Service adminProductV2Service;
    private final PermissionGuard permissionGuard;

    public AdminProductV2Controller(ProductV2Service productV2Service,
                                    AdminProductService adminProductService,
                                    AdminProductV2Service adminProductV2Service,
                                    PermissionGuard permissionGuard) {
        this.productV2Service = productV2Service;
        this.adminProductService = adminProductService;
        this.adminProductV2Service = adminProductV2Service;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ProductV2PageResponse listProducts(@RequestParam(defaultValue = "1") @Min(1) int page,
                                              @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long ownerUserId,
                                              @RequestParam(required = false) String categoryCode,
                                              @RequestParam(required = false) String conditionLevel,
                                              @RequestParam(required = false) String tradeMode,
                                              @RequestParam(required = false) String campusCode,
                                              @RequestParam(required = false) String sortBy,
                                              @RequestParam(required = false) String sortDir) {
        return productV2Service.listProductsForAdmin(keyword, status, ownerUserId, categoryCode, conditionLevel,
                tradeMode, campusCode, sortBy, sortDir, page, size);
    }

    @GetMapping("/{productId}")
    public ProductV2DetailResponse getProductDetail(@PathVariable Long productId) {
        return productV2Service.getProductDetailForAdmin(productId);
    }

    @PostMapping("/{productId}/off-shelf")
    public ProductWriteResponse forceOffShelf(@PathVariable Long productId,
                                              @RequestBody(required = false) AdminProductOffShelfRequest request,
                                              Authentication authentication,
                                              HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.off_shelf", httpServletRequest::getHeader);
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        String reason = request == null ? null : request.reason();
        return adminProductService.forceOffShelf(productId, operatorUserId, reason);
    }

    @PostMapping("/batch-off-shelf")
    public ProductV2BatchOffShelfResponse batchOffShelf(@Valid @RequestBody ProductV2BatchOffShelfRequest request,
                                                        Authentication authentication,
                                                        HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.off_shelf", httpServletRequest::getHeader);
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        return adminProductV2Service.batchForceOffShelf(request.productIds(), operatorUserId, request.reason());
    }
}
