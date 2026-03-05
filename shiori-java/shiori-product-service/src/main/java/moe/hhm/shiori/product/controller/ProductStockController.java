package moe.hhm.shiori.product.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.product.dto.StockDeductRequest;
import moe.hhm.shiori.product.dto.StockOperateResponse;
import moe.hhm.shiori.product.dto.StockReleaseRequest;
import moe.hhm.shiori.product.security.CurrentUserSupport;
import moe.hhm.shiori.product.service.ProductStockService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product/internal/stock")
public class ProductStockController {

    private final ProductStockService productStockService;
    private final PermissionGuard permissionGuard;

    public ProductStockController(ProductStockService productStockService,
                                  PermissionGuard permissionGuard) {
        this.productStockService = productStockService;
        this.permissionGuard = permissionGuard;
    }

    @PostMapping("/deduct")
    public StockOperateResponse deduct(@Valid @RequestBody StockDeductRequest request,
                                       Authentication authentication,
                                       HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.stock.adjust", httpServletRequest::getHeader);
        CurrentUserSupport.requireUserId(authentication);
        return productStockService.deduct(request);
    }

    @PostMapping("/release")
    public StockOperateResponse release(@Valid @RequestBody StockReleaseRequest request,
                                        Authentication authentication,
                                        HttpServletRequest httpServletRequest) {
        permissionGuard.require("product.stock.adjust", httpServletRequest::getHeader);
        CurrentUserSupport.requireUserId(authentication);
        return productStockService.release(request);
    }
}
