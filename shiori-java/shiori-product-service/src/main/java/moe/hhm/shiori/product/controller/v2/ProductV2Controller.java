package moe.hhm.shiori.product.controller.v2;

import jakarta.validation.Valid;
import moe.hhm.shiori.product.dto.ProductWriteResponse;
import moe.hhm.shiori.product.dto.v2.CreateProductV2Request;
import moe.hhm.shiori.product.dto.v2.ProductV2DetailResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2PageResponse;
import moe.hhm.shiori.product.dto.v2.UpdateProductV2Request;
import moe.hhm.shiori.product.security.CurrentUserSupport;
import moe.hhm.shiori.product.service.ProductV2Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "feature.api-v2", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v2/product/products")
public class ProductV2Controller {

    private final ProductV2Service productV2Service;

    public ProductV2Controller(ProductV2Service productV2Service) {
        this.productV2Service = productV2Service;
    }

    @GetMapping
    public ProductV2PageResponse list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String categoryCode,
                                      @RequestParam(required = false) String conditionLevel,
                                      @RequestParam(required = false) String tradeMode,
                                      @RequestParam(required = false) String campusCode,
                                      @RequestParam(required = false) String sortBy,
                                      @RequestParam(required = false) String sortDir) {
        return productV2Service.listOnSaleProducts(keyword, categoryCode, conditionLevel, tradeMode, campusCode,
                sortBy, sortDir, page, size);
    }

    @GetMapping("/{productId}")
    public ProductV2DetailResponse detail(@PathVariable Long productId) {
        return productV2Service.getOnSaleProductDetail(productId);
    }

    @PostMapping
    public ProductWriteResponse create(@Valid @RequestBody CreateProductV2Request request, Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return productV2Service.createProduct(userId, request);
    }

    @PutMapping("/{productId}")
    public ProductWriteResponse update(@PathVariable Long productId,
                                       @Valid @RequestBody UpdateProductV2Request request,
                                       Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        boolean admin = CurrentUserSupport.hasRoleAdmin(authentication);
        return productV2Service.updateProduct(productId, userId, admin, request);
    }

    @PostMapping("/{productId}/publish")
    public ProductWriteResponse publish(@PathVariable Long productId, Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        boolean admin = CurrentUserSupport.hasRoleAdmin(authentication);
        return productV2Service.publishProduct(productId, userId, admin);
    }

    @PostMapping("/{productId}/off-shelf")
    public ProductWriteResponse offShelf(@PathVariable Long productId, Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        boolean admin = CurrentUserSupport.hasRoleAdmin(authentication);
        return productV2Service.offShelfProduct(productId, userId, admin);
    }
}
