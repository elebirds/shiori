package moe.hhm.shiori.product.controller;

import jakarta.servlet.http.HttpServletRequest;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.dto.ProductSearchReindexResponse;
import moe.hhm.shiori.product.security.CurrentUserSupport;
import moe.hhm.shiori.product.service.ProductSearchReindexService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/product/internal/search")
public class ProductSearchInternalController {

    private final ProductSearchReindexService reindexService;
    private final PermissionGuard permissionGuard;

    public ProductSearchInternalController(ProductSearchReindexService reindexService,
                                           PermissionGuard permissionGuard) {
        this.reindexService = reindexService;
        this.permissionGuard = permissionGuard;
    }

    @PostMapping("/reindex")
    public ProductSearchReindexResponse reindex(@RequestParam(defaultValue = "200") int batchSize,
                                                Authentication authentication,
                                                HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.search.reindex", httpServletRequest::getHeader);
        CurrentUserSupport.requireUserId(authentication);
        return reindexService.reindexAllOnSaleProducts(batchSize);
    }
}
