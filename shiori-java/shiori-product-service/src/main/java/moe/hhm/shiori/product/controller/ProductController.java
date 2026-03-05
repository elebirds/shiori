package moe.hhm.shiori.product.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.dto.CreateProductRequest;
import moe.hhm.shiori.product.dto.ProductDetailResponse;
import moe.hhm.shiori.product.dto.ProductPageResponse;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.UpdateProductRequest;
import moe.hhm.shiori.product.security.CurrentUserSupport;
import moe.hhm.shiori.product.service.ProductService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product/products")
public class ProductController {

    private final ProductService productService;
    private final PermissionGuard permissionGuard;

    public ProductController(ProductService productService, PermissionGuard permissionGuard) {
        this.productService = productService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ProductPageResponse list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String keyword) {
        return productService.listOnSaleProducts(keyword, page, size);
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse detail(@PathVariable Long productId) {
        return productService.getOnSaleProductDetail(productId);
    }

    @PostMapping
    public ProductWriteResponse create(@Valid @RequestBody CreateProductRequest request,
                                       Authentication authentication,
                                       HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.create", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return productService.createProduct(userId, request);
    }

    @PutMapping("/{productId}")
    public ProductWriteResponse update(@PathVariable Long productId,
                                       @Valid @RequestBody UpdateProductRequest request,
                                       Authentication authentication,
                                       HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.update", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        boolean admin = CurrentUserSupport.hasRoleAdmin(authentication);
        return productService.updateProduct(productId, userId, admin, request);
    }

    @PostMapping("/{productId}/publish")
    public ProductWriteResponse publish(@PathVariable Long productId,
                                        Authentication authentication,
                                        HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.publish", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        boolean admin = CurrentUserSupport.hasRoleAdmin(authentication);
        return productService.publishProduct(productId, userId, admin);
    }

    @PostMapping("/{productId}/off-shelf")
    public ProductWriteResponse offShelf(@PathVariable Long productId,
                                         Authentication authentication,
                                         HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.off_shelf", httpServletRequest::getHeader);
        Long userId = CurrentUserSupport.requireUserId(authentication);
        boolean admin = CurrentUserSupport.hasRoleAdmin(authentication);
        return productService.offShelfProduct(productId, userId, admin);
    }
}
