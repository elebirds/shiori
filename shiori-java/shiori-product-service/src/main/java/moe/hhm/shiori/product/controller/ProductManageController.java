package moe.hhm.shiori.product.controller;

import moe.hhm.shiori.product.dto.ProductDetailResponse;
import moe.hhm.shiori.product.dto.ProductPageResponse;
import moe.hhm.shiori.product.security.CurrentUserSupport;
import moe.hhm.shiori.product.service.ProductService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product/my/products")
public class ProductManageController {

    private final ProductService productService;

    public ProductManageController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ProductPageResponse listMyProducts(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String status,
                                              Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return productService.listMyProducts(userId, keyword, status, page, size);
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse getMyProductDetail(@PathVariable Long productId, Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        boolean admin = CurrentUserSupport.hasRoleAdmin(authentication);
        return productService.getMyProductDetail(productId, userId, admin);
    }
}

