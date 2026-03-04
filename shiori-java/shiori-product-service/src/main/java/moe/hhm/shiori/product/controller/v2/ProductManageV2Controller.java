package moe.hhm.shiori.product.controller.v2;

import moe.hhm.shiori.product.dto.v2.ProductV2DetailResponse;
import moe.hhm.shiori.product.dto.v2.ProductV2PageResponse;
import moe.hhm.shiori.product.security.CurrentUserSupport;
import moe.hhm.shiori.product.service.ProductV2Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "feature.api-v2", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v2/product/my/products")
public class ProductManageV2Controller {

    private final ProductV2Service productV2Service;

    public ProductManageV2Controller(ProductV2Service productV2Service) {
        this.productV2Service = productV2Service;
    }

    @GetMapping
    public ProductV2PageResponse listMyProducts(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String categoryCode,
                                                @RequestParam(required = false) String conditionLevel,
                                                @RequestParam(required = false) String tradeMode,
                                                @RequestParam(required = false) String campusCode,
                                                @RequestParam(required = false) String sortBy,
                                                @RequestParam(required = false) String sortDir,
                                                Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        return productV2Service.listMyProducts(userId, keyword, status, categoryCode, conditionLevel, tradeMode,
                campusCode, sortBy, sortDir, page, size);
    }

    @GetMapping("/{productId}")
    public ProductV2DetailResponse getMyProductDetail(@PathVariable Long productId, Authentication authentication) {
        Long userId = CurrentUserSupport.requireUserId(authentication);
        boolean admin = CurrentUserSupport.hasRoleAdmin(authentication);
        return productV2Service.getMyProductDetail(productId, userId, admin);
    }
}
