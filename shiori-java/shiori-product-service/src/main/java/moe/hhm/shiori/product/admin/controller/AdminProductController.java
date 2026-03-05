package moe.hhm.shiori.product.admin.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.servlet.http.HttpServletRequest;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.admin.dto.AdminProductOffShelfRequest;
import moe.hhm.shiori.product.admin.service.AdminProductService;
import moe.hhm.shiori.product.dto.ProductDetailResponse;
import moe.hhm.shiori.product.dto.ProductPageResponse;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.security.CurrentUserSupport;
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
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final AdminProductService adminProductService;
    private final PermissionGuard permissionGuard;

    public AdminProductController(AdminProductService adminProductService,
                                  PermissionGuard permissionGuard) {
        this.adminProductService = adminProductService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ProductPageResponse listProducts(@RequestParam(defaultValue = "1") @Min(1) int page,
                                            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long ownerUserId) {
        return adminProductService.listProducts(keyword, status, ownerUserId, page, size);
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse getProductDetail(@PathVariable Long productId, Authentication authentication) {
        Long operatorUserId = CurrentUserSupport.requireUserId(authentication);
        return adminProductService.getProductDetail(productId, operatorUserId);
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
}
